package br.com.reservas.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.reservas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** RNF-02/D-26: CORS restrito a `app.public-url` (default http://localhost). */
@SpringBootTest
@AutoConfigureMockMvc
class CorsConfigMockMvcTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("RNF-02: origem permitida (app.public-url) recebe Access-Control-Allow-Origin e credenciais")
    void allowedOriginGetsCorsHeaders() throws Exception {
        // O backend roda na porta 8080 internamente (server.port); a requisicao
        // so e tratada como cross-origin pelo Spring se a porta da propria
        // requisicao difere da porta implicita do header Origin (80).
        var result = mockMvc.perform(get("/api/v1/system/clock")
                .header(HttpHeaders.ORIGIN, "http://localhost")
                .with(request -> {
                    request.setServerPort(8080);
                    return request;
                }))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("http://localhost");
        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
    }

    @Test
    @DisplayName("RNF-02: origem nao cadastrada nao recebe Access-Control-Allow-Origin")
    void disallowedOriginGetsNoCorsHeader() throws Exception {
        var result = mockMvc.perform(get("/api/v1/system/clock").header(HttpHeaders.ORIGIN, "http://evil.test"))
            .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
