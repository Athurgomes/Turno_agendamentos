package br.com.reservas.shared.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import br.com.reservas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/** RNF-11: correlacao de logs por requisicao via {@code X-Request-Id}. */
@SpringBootTest
@AutoConfigureMockMvc
class CorrelationIdFilterMockMvcTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("RNF-11: X-Request-Id enviado pelo cliente e devolvido igual na resposta")
    void echoesClientProvidedRequestId() throws Exception {
        var result = mockMvc.perform(get("/api/v1/system/clock").header(CorrelationIdFilter.HEADER, "meu-id-123"))
            .andReturn();

        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).isEqualTo("meu-id-123");
    }

    @Test
    @DisplayName("RNF-11: sem X-Request-Id do cliente, o backend gera um novo id na resposta")
    void generatesRequestIdWhenMissing() throws Exception {
        var result = mockMvc.perform(get("/api/v1/system/clock").with(user("admin"))).andReturn();

        assertThat(result.getResponse().getHeader(CorrelationIdFilter.HEADER)).isNotBlank();
    }
}
