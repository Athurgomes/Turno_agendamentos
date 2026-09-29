package br.com.reservas.settings.api;

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

/** `/admin/settings` e `/settings/public` (D-12, D-43, F2-7): validacoes, auditoria e visibilidade do WhatsApp. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SettingsControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteAdmin1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID condominiumId;
    private String adminToken;
    private String unitToken;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Settings");
        jdbcTemplate.update("insert into condominium_settings (condominium_id) values (?)", condominiumId);

        String adminLogin = "admin.settings." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminLogin, passwordEncoder.encode(PASSWORD));
        adminToken = ApiLogin.token(mockMvc, objectMapper, adminLogin, PASSWORD);

        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update("insert into unit (id, condominium_id, number, identifier) values (?, ?, ?, ?)",
            unitId, condominiumId, "settings1", "settings1");
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', 'settings1', ?, ?)",
            condominiumId, unitId, passwordEncoder.encode(PASSWORD));
        unitToken = ApiLogin.token(mockMvc, objectMapper, "settings1", PASSWORD);
    }

    private String updatePayload(int minAdvanceDays, String windowStart, String windowEnd, int maxAdvanceDays) {
        return """
            {
              "condominiumName": "Residencial Atualizado",
              "defaultPaymentWhatsapp": "556299998888",
              "minAdvanceDays": %d,
              "nextDayWindowStart": "%s",
              "nextDayWindowEnd": "%s",
              "maxAdvanceDays": %d,
              "maxActiveBookingsPerUnit": 3,
              "residentCancelDeadlineHours": 24,
              "reportWindowDays": 7
            }
            """.formatted(minAdvanceDays, windowStart, windowEnd, maxAdvanceDays);
    }

    @Test
    @DisplayName("D-43/F2-7: ADMIN altera os parametros e a mudanca aparece em /settings/public")
    void adminUpdateReflectsOnPublicSettings() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload(2, "07:00", "15:00", 90)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minAdvanceDays").value(2))
            .andExpect(jsonPath("$.condominiumName").value("Residencial Atualizado"))
            .andExpect(jsonPath("$.nextDayWindowStart").value("07:00"));

        mockMvc.perform(get("/api/v1/settings/public")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.minAdvanceDays").value(2))
            .andExpect(jsonPath("$.maxAdvanceDays").value(90))
            .andExpect(jsonPath("$.nextDayWindowStart").value("07:00"))
            .andExpect(jsonPath("$.defaultPaymentWhatsapp").doesNotExist());
    }

    @Test
    @DisplayName("D-44: PUT /admin/settings aceita horario 'HH:mm:ss' e devolve 'HH:mm'")
    void updateAcceptsHourWithSecondsAndReturnsHourMinute() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload(2, "07:30:00", "15:00:00", 90)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextDayWindowStart").value("07:30"))
            .andExpect(jsonPath("$.nextDayWindowEnd").value("15:00"));
    }

    @Test
    @DisplayName("D-43: maxAdvanceDays menor que minAdvanceDays -> 422 VALIDATION_ERROR")
    void maxAdvanceDaysLessThanMinIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload(10, "06:00", "16:00", 5)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("D-43: janela do dia seguinte com inicio depois do fim -> 422 VALIDATION_ERROR")
    void nextDayWindowStartAfterEndIsRejected() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload(1, "18:00", "06:00", 60)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RN-01: token UNIT recebe 403 em /admin/settings")
    void unitTokenIsForbiddenOnAdminSettings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RNF-07: PUT /admin/settings grava auditoria com antes/depois")
    void updateRecordsAuditTrail() throws Exception {
        mockMvc.perform(put("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(updatePayload(3, "06:00", "16:00", 45)))
            .andExpect(status().isOk());

        Integer count = jdbcTemplate.queryForObject(
            "select count(*) from audit_log where action = 'SETTINGS_UPDATE' and condominium_id = ?",
            Integer.class, condominiumId);
        org.assertj.core.api.Assertions.assertThat(count).isEqualTo(1);
    }
}
