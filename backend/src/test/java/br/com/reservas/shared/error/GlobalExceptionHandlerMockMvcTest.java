package br.com.reservas.shared.error;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contrato de erros de docs/03 ("Convencoes transversais"): todo erro vira
 * `application/problem+json` com `code`. Requisicoes autenticadas usam um
 * usuario de teste (a autenticacao real so chega na F1); o objetivo aqui e
 * validar so o {@code GlobalExceptionHandler}.
 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerMockMvcTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Contrato docs/03: bean validation vira 400 VALIDATION_ERROR com errors[]")
    void validationErrorReturnsFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/validation")
                .with(user("morador"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("Contrato docs/03: JSON malformado vira 400 VALIDATION_ERROR")
    void malformedJsonReturnsValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/test-errors/validation")
                .with(user("morador"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Contrato docs/03: BusinessException preserva HTTP status, code e mensagem pt-BR")
    void businessExceptionKeepsCodeAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/business").with(user("admin")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("SAMPLE_CODE"))
            .andExpect(jsonPath("$.detail").value("Mensagem de exemplo em pt-BR."));
    }

    @Test
    @DisplayName("Contrato docs/03: entidade nao encontrada vira 404 NOT_FOUND")
    void entityNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/not-found").with(user("admin")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("Contrato docs/03: lock otimista vira 409 CONFLICT")
    void optimisticLockingReturns409Conflict() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/optimistic-lock").with(user("admin")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("RN-24: violacao da exclusion constraint (SQLState 23P01) vira 409 RESERVATION_OVERLAP")
    void exclusionViolationReturnsReservationOverlap() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/overlap").with(user("morador")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_OVERLAP"))
            .andExpect(jsonPath("$.detail").value("Já existe uma reserva ou bloqueio nesse horário."));
    }

    @Test
    @DisplayName("Contrato docs/03: violacao de unicidade generica vira 409 CONFLICT")
    void genericDataIntegrityViolationReturns409Conflict() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/generic-conflict").with(user("admin")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("RN-01: AccessDeniedException lancada num caso de uso vira 403 FORBIDDEN")
    void accessDeniedReturns403Forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/access-denied").with(user("morador")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("Contrato docs/03: excecao inesperada vira 500 INTERNAL_ERROR sem vazar a mensagem interna")
    void unexpectedExceptionReturns500WithoutLeakingMessage() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/boom").with(user("admin")))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.detail").value(not("detalhe interno que nao pode vazar")));
    }

    @Test
    @DisplayName("RN-06: sem token numa rota protegida vira 401 UNAUTHENTICATED no formato ProblemDetail")
    void unauthenticatedRequestReturns401Problem() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/business"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("RNF-11/docs-03: parametro de path com tipo invalido vira 400 VALIDATION_ERROR sem ecoar o valor bruto")
    void typeMismatchReturnsValidationErrorWithoutEchoingRawValue() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/type-mismatch/not-a-uuid").with(user("admin")))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("id"))
            .andExpect(jsonPath("$.errors[0].message").value("Valor inválido."))
            .andExpect(jsonPath("$.detail").value(not(org.hamcrest.Matchers.containsString("not-a-uuid"))));
    }

    @Test
    @DisplayName("RNF-11/docs-03: metodo HTTP nao suportado vira resposta ProblemDetail com code")
    void methodNotSupportedReturnsProblemDetailWithCode() throws Exception {
        mockMvc.perform(get("/api/v1/test-errors/validation").with(user("morador")))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.code").exists());
    }
}
