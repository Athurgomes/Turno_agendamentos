package br.com.reservas.flow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Fluxo de parametros do condominio (F2-7, D-43): ADMIN muda os parametros e /settings/public reflete. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("D-43/F2-7: ADMIN muda maxActiveBookingsPerUnit e horarios; /settings/public reflete em 'HH:mm'")
    void adminChangesMaxActiveBookingsAndPublicSettingsReflect() throws Exception {
        UUID condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId,
            "Condominio Settings Flow");
        jdbcTemplate.update("insert into condominium_settings (condominium_id) values (?)", condominiumId);

        String adminLogin = "admin.settingsflow." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminLogin, passwordEncoder.encode(PASSWORD));
        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminLogin, PASSWORD);

        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update("insert into unit (id, condominium_id, number, identifier) values (?, ?, ?, ?)",
            unitId, condominiumId, "sf1", "sf1");
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', 'sf1', ?, ?)",
            condominiumId, unitId, passwordEncoder.encode(PASSWORD));
        String unitToken = ApiLogin.token(mockMvc, objectMapper, "sf1", PASSWORD);

        String payload = """
            {
              "condominiumName": "Residencial Fluxo",
              "defaultPaymentWhatsapp": "556299998888",
              "minAdvanceDays": 1,
              "nextDayWindowStart": "07:00:00",
              "nextDayWindowEnd": "15:00:00",
              "maxAdvanceDays": 60,
              "maxActiveBookingsPerUnit": 7,
              "residentCancelDeadlineHours": 24,
              "reportWindowDays": 7
            }
            """;

        mockMvc.perform(put("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxActiveBookingsPerUnit").value(7))
            .andExpect(jsonPath("$.nextDayWindowStart").value("07:00"));

        mockMvc.perform(get("/api/v1/settings/public")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxActiveBookingsPerUnit").value(7))
            .andExpect(jsonPath("$.nextDayWindowStart").value("07:00"))
            .andExpect(jsonPath("$.nextDayWindowEnd").value("15:00"))
            .andExpect(jsonPath("$.defaultPaymentWhatsapp").doesNotExist());
    }
}
