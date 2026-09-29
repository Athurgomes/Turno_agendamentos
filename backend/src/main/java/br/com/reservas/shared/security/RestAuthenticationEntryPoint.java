package br.com.reservas.shared.security;

import br.com.reservas.shared.error.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Requisição sem token, token inválido ou expirado (RN-06). Exceções de
 * autenticação lançadas pelo filtro de segurança não chegam ao
 * {@code GlobalExceptionHandler} (rodam antes do DispatcherServlet), por isso
 * este componente monta o mesmo {@code ProblemDetail} manualmente.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
        AuthenticationException authException) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        var problem = ProblemDetailFactory.of(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED",
            "Autenticação necessária ou token inválido/expirado.");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
