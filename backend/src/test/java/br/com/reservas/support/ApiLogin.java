package br.com.reservas.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Helper de login reutilizado pelos testes de integracao dos modulos F2+
 * (unit, settings): cada chamada usa um IP diferente (contador estatico, unico
 * no processo de teste) porque o {@code LoginRateLimiter} e um bean singleton
 * compartilhado entre classes de teste que reusam o mesmo `ApplicationContext`
 * (ver comentario identico em {@code AuthControllerRefreshTest}).
 */
public final class ApiLogin {

    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    private ApiLogin() {
    }

    public static String token(MockMvc mockMvc, ObjectMapper objectMapper, String login, String password)
        throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "198.51.100.40." + IP_SEQUENCE.incrementAndGet())
                .content(objectMapper.writeValueAsString(Map.of("login", login, "password", password))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
