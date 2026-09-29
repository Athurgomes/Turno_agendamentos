package br.com.reservas.shared.error.support;

import br.com.reservas.shared.error.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.sql.SQLException;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller so de teste (test sources) para exercitar o {@code GlobalExceptionHandler}
 * de ponta a ponta via MockMvc, sem depender de nenhum modulo de negocio ainda inexistente.
 */
@RestController
@RequestMapping("/api/v1/test-errors")
public class TestErrorController {

    @PostMapping("/validation")
    public String validation(@Valid @RequestBody Payload payload) {
        return payload.name();
    }

    @GetMapping("/business")
    public String business() {
        throw new BusinessException("SAMPLE_CODE", HttpStatus.UNPROCESSABLE_ENTITY, "Mensagem de exemplo em pt-BR.");
    }

    @GetMapping("/not-found")
    public String notFound() {
        throw new EntityNotFoundException("nao existe");
    }

    @GetMapping("/optimistic-lock")
    public String optimisticLock() {
        throw new OptimisticLockingFailureException("versao divergente");
    }

    @GetMapping("/overlap")
    public String overlap() throws SQLException {
        throw new DataIntegrityViolationException("conflito", new SQLException("exclusion", "23P01"));
    }

    @GetMapping("/generic-conflict")
    public String genericConflict() {
        throw new DataIntegrityViolationException("unicidade violada");
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        throw new AccessDeniedException("sem permissao");
    }

    @GetMapping("/boom")
    public String boom() {
        throw new RuntimeException("detalhe interno que nao pode vazar");
    }

    @GetMapping("/type-mismatch/{id}")
    public String typeMismatch(@PathVariable UUID id) {
        return id.toString();
    }

    public record Payload(@NotBlank(message = "nome e obrigatorio") String name) {
    }
}
