package br.com.reservas.shared.system;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * D-32/FD-3: no perfil {@code demo} com {@code APP_DEMO_NOW} definido,
 * {@code /system/clock} responde o relogio simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"local", "demo"})
@TestPropertySource(properties = "app.demo.now=2026-11-10T10:00")
class DemoClockSystemControllerTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("D-32: perfil demo com APP_DEMO_NOW ativo, /system/clock responde simulated=true "
        + "e now no instante configurado (fuso America/Sao_Paulo = UTC-3)")
    void demoClockIsSimulated() throws Exception {
        mockMvc.perform(get("/api/v1/system/clock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.simulated").value(true))
            .andExpect(jsonPath("$.now").value(startsWith("2026-11-10T13:0")));
    }
}
