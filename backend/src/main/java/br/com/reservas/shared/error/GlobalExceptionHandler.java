package br.com.reservas.shared.error;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.sql.SQLException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduz toda exceção lançada pelos módulos para {@code application/problem+json}
 * com o campo {@code code} (RNF: contrato de erros em docs/03). Exceções lançadas
 * pelo filtro de segurança (401/403 antes do DispatcherServlet) NÃO passam por
 * aqui: veja {@code RestAuthenticationEntryPoint}/{@code RestAccessDeniedHandler}
 * em {@code shared.security}, que geram o mesmo formato de resposta.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException ex) {
        return ProblemDetailFactory.of(ex.status(), ex.code(), ex.getMessage());
    }

    // P-11/D-44: 409 com a lista de reservas afetadas embutida no ProblemDetail
    // (mesmo campo `affectedReservations` usado pela exclusão de área e de unidade).
    @ExceptionHandler(br.com.reservas.unit.application.UnitHasFutureReservationsException.class)
    public ProblemDetail handleUnitHasFutureReservations(
        br.com.reservas.unit.application.UnitHasFutureReservationsException ex) {
        ProblemDetail problem = ProblemDetailFactory.of(HttpStatus.CONFLICT, "UNIT_HAS_FUTURE_RESERVATIONS",
            "A unidade tem reservas futuras ativas; cancele-as antes de desativar.");
        problem.setProperty("affectedReservations", ex.affectedReservations());
        return problem;
    }

    // RN-16/D-44: mesmo campo `affectedReservations` do 409 de unidade, para a exclusão/mudança de status de área.
    @ExceptionHandler(br.com.reservas.area.application.AreaHasFutureReservationsException.class)
    public ProblemDetail handleAreaHasFutureReservations(
        br.com.reservas.area.application.AreaHasFutureReservationsException ex) {
        ProblemDetail problem = ProblemDetailFactory.of(HttpStatus.CONFLICT, "AREA_HAS_FUTURE_RESERVATIONS",
            "A área tem reservas futuras ativas; confirme o cancelamento em lote com uma justificativa.");
        problem.setProperty("affectedReservations", ex.affectedReservations());
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        List<FieldErrorDto> errors = ex.getConstraintViolations().stream()
            .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), v.getMessage()))
            .toList();
        return validationProblem(errors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        return ProblemDetailFactory.of(HttpStatus.FORBIDDEN, "FORBIDDEN",
            "Você não tem permissão para executar esta ação.");
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleEntityNotFound(EntityNotFoundException ex) {
        return ProblemDetailFactory.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado.");
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(OptimisticLockingFailureException ex) {
        return ProblemDetailFactory.of(HttpStatus.CONFLICT, "CONFLICT",
            "O recurso foi alterado por outra pessoa; recarregue e tente novamente.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        if (isExclusionViolation(ex)) {
            return ProblemDetailFactory.of(HttpStatus.CONFLICT, "RESERVATION_OVERLAP",
                "Já existe uma reserva ou bloqueio nesse horário.");
        }
        return ProblemDetailFactory.of(HttpStatus.CONFLICT, "CONFLICT",
            "Esse registro conflita com outro já existente.");
    }

    // Sem @ExceptionHandler: MaxUploadSizeExceededException já é tratada pelo
    // handleException(...) herdado (ela implementa ErrorResponse desde o Spring
    // 6); customizamos o corpo em handleExceptionInternal, senão o registro
    // fica ambíguo (dois métodos mapeados para o mesmo tipo).
    ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        return ProblemDetailFactory.of(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_FILE",
            "Arquivo maior que o limite permitido.");
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Erro interno não tratado", ex);
        return ProblemDetailFactory.of(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
            "Erro interno. Tente novamente mais tarde.");
    }

    // RN: SQLState 23P01 = exclusion_violation (Postgres), usado pela constraint
    // anti-sobreposição de reservas/bloqueios (RN-24) via btree_gist.
    private boolean isExclusionViolation(DataIntegrityViolationException ex) {
        Throwable root = ex.getRootCause();
        return root instanceof SQLException sqlException && "23P01".equals(sqlException.getSQLState());
    }

    // --- Erros de validação/parsing tratados pelo Spring MVC base class ---

    @Override
    protected org.springframework.http.ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldErrorDto> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> new FieldErrorDto(fe.getField(), fe.getDefaultMessage()))
            .toList();
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(validationProblem(errors));
    }

    @Override
    protected org.springframework.http.ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(validationProblem(List.of(new FieldErrorDto("body", "JSON inválido ou malformado."))));
    }

    // RNF-11/docs-03: parâmetro de path/query com tipo incompatível (ex.: UUID
    // inválido) vira 400 VALIDATION_ERROR, sem ecoar o valor bruto enviado.
    @Override
    protected org.springframework.http.ResponseEntity<Object> handleTypeMismatch(
        TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String field = ex instanceof MethodArgumentTypeMismatchException matme ? matme.getName() : "parâmetro";
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(validationProblem(List.of(new FieldErrorDto(field, "Valor inválido."))));
    }

    // RNF-11/docs-03: parâmetro obrigatório ausente vira 400 VALIDATION_ERROR.
    @Override
    protected org.springframework.http.ResponseEntity<Object> handleMissingServletRequestParameter(
        MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatusCode status,
        WebRequest request) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(validationProblem(
                List.of(new FieldErrorDto(ex.getParameterName(), "Parâmetro obrigatório não informado."))));
    }

    @Override
    protected org.springframework.http.ResponseEntity<Object> handleNoResourceFoundException(
        NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return org.springframework.http.ResponseEntity.status(HttpStatus.NOT_FOUND)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(ProblemDetailFactory.of(HttpStatus.NOT_FOUND, "NOT_FOUND", "Recurso não encontrado."));
    }

    @Override
    protected org.springframework.http.ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
        HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        if (ex instanceof MaxUploadSizeExceededException maxUpload) {
            ProblemDetail problem = handleMaxUploadSizeExceeded(maxUpload);
            return org.springframework.http.ResponseEntity.status(problem.getStatus())
                .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
        }
        // RNF-11/docs-03: qualquer outra exceção tratada pela classe base do
        // Spring MVC (ex.: 405 método não suportado, 415 media type) também
        // precisa sair no formato ProblemDetail com `code`. Não há code
        // específico documentado para 405/415 em docs/03: reaproveita
        // VALIDATION_ERROR (4xx) ou INTERNAL_ERROR (5xx), os mais próximos da
        // tabela existente, em vez de inventar um code novo.
        String code = statusCode.is5xxServerError() ? "INTERNAL_ERROR" : "VALIDATION_ERROR";
        String detail = statusCode.is5xxServerError() ? "Erro interno. Tente novamente mais tarde."
            : "Requisição inválida.";
        ProblemDetail problem = ProblemDetailFactory.of(HttpStatus.valueOf(statusCode.value()), code, detail);
        return org.springframework.http.ResponseEntity.status(statusCode)
            .headers(headers)
            .contentType(org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON)
            .body(problem);
    }

    private ProblemDetail validationProblem(List<FieldErrorDto> errors) {
        ProblemDetail problem = ProblemDetailFactory.of(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
            "Dados inválidos.");
        problem.setProperty("errors", errors);
        return problem;
    }
}
