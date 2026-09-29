package br.com.reservas.shared.security;

import br.com.reservas.shared.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Perfil autenticado sem permissão para a rota (403 FORBIDDEN). Assim como o
 * {@link RestAuthenticationEntryPoint}, roda dentro do filtro de segurança,
 * antes do DispatcherServlet, então monta o {@code ProblemDetail} sem passar
 * pelo {@code GlobalExceptionHandler}.
 */
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public RestAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
        AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var problem = ProblemDetailFactory.of(HttpStatus.FORBIDDEN, "FORBIDDEN",
            "Você não tem permissão para executar esta ação.");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
