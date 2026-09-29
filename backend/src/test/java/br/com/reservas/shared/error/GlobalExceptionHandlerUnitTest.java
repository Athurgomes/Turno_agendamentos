package br.com.reservas.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** RN-17: teste puro (sem contexto Spring) do mapeamento de upload grande demais. */
class GlobalExceptionHandlerUnitTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("RN-17: arquivo maior que o limite vira 422 INVALID_FILE")
    void maxUploadSizeExceededReturnsInvalidFile() {
        ProblemDetail problem = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024L));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY.value());
        assertThat(problem.getProperties()).containsEntry("code", "INVALID_FILE");
    }
}
