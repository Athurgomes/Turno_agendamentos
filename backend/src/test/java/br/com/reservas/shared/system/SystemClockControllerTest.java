package br.com.reservas.shared.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** D-40: `GET /api/v1/system/clock` e publico (RNF-10) e usa o {@link Clock} injetado. */
@SpringBootTest
@AutoConfigureMockMvc
class SystemClockControllerTest extends AbstractIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-15T12:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("RNF-10: sem condominio cadastrado, /system/clock responde o fuso default America/Sao_Paulo")
    void respondsWithDefaultTimezoneAndSimulatedFalse() throws Exception {
        mockMvc.perform(get("/api/v1/system/clock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.now").value("2026-01-15T12:00:00Z"))
            .andExpect(jsonPath("$.timezone").value("America/Sao_Paulo"))
            .andExpect(jsonPath("$.simulated").value(false));
    }

    @Test
    @Transactional
    @DisplayName("RNF-10: com condominio cadastrado, /system/clock responde o fuso do condominio")
    void respondsWithCondominiumTimezone() throws Exception {
        jdbcTemplate.update(
            "INSERT INTO condominium (id, name, timezone) VALUES (?, ?, ?)",
            UUID.randomUUID(), "Condominio Ficticio Clock", "America/Manaus");

        mockMvc.perform(get("/api/v1/system/clock"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.timezone").value("America/Manaus"));
    }
}
