package br.com.reservas.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

/**
 * RN-01/RNF-02: excecao de autorizacao lancada pelo proprio filtro de
 * seguranca (antes do DispatcherServlet), por isso testada diretamente no
 * handler em vez de via MockMvc (nao ha rota com restricao de papel ainda,
 * a autenticacao/autorizacao real so chega na F1).
 */
class RestAccessDeniedHandlerTest {

    private final RestAccessDeniedHandler handler = new RestAccessDeniedHandler(new ObjectMapper());

    @Test
    @DisplayName("Contrato docs/03: FORBIDDEN em application/problem+json com code")
    void writesForbiddenProblemDetail() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("sem permissao"));

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }
}
