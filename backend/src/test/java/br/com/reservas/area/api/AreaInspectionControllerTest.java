package br.com.reservas.area.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.AreaTestStorageConfig;
import br.com.reservas.area.support.TestAreaReservationsGateway;
import br.com.reservas.area.support.TestImages;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** `/areas/{id}/inspections` (RF-ARE-07). */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AreaTestStorageConfig.class, TestAreaReservationsGateway.Config.class})
@Transactional
class AreaInspectionControllerTest extends AbstractIntegrationTest {

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
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId,
            "Condominio Vistorias");

        adminToken = createAccount("ADMIN", null);
        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into unit (id, condominium_id, block, number, identifier) values (?, ?, 'A', '1', 'a-1')",
            unitId, condominiumId);
        unitToken = createAccount("UNIT", unitId);

        areaId = createArea();
    }

    private String createAccount(String role, UUID unitId) throws Exception {
        boolean isUnit = "UNIT".equals(role);
        String username = isUnit ? "u-" + UUID.randomUUID().toString().substring(0, 8) : null;
        String email = isUnit ? null : role.toLowerCase() + "." + UUID.randomUUID() + "@exemplo.test";
        String login = isUnit ? username : email;
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, email, display_name, unit_id, password_hash) "
                + "values (?, ?, ?, ?, ?, ?, ?)",
            condominiumId, role, username, email, role + " Teste", unitId, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, login, PASSWORD);
    }

    private UUID createArea() throws Exception {
        String dataJson = """
            {
              "name": "Quadra %s",
              "category": "SPORTS_COURT",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 10,
              "requiresPayment": false,
              "openingHours": [ { "dayOfWeek": 6, "openTime": "10:00", "closeTime": "22:00" } ]
            }
            """.formatted(UUID.randomUUID());
        String response = mockMvc.perform(multipart("/api/v1/areas")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .file(new MockMultipartFile("photos", "a.png", "image/png", TestImages.png()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    @Test
    @DisplayName("RN-01/D-20: UNIT recebe 403 em /areas/{id}/inspections")
    void unitCannotAccessInspections() throws Exception {
        mockMvc.perform(get("/api/v1/areas/" + areaId + "/inspections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RF-ARE-07: ADMIN registra vistoria com foto; foto herda inspectionId e takenAt = inspectedAt")
    void adminCreatesInspectionWithPhoto() throws Exception {
        String dataJson = """
            { "inspectedAt": "2026-05-10", "overallCondition": "GOOD", "notes": "Tudo certo" }
            """;
        String response = mockMvc.perform(multipart("/api/v1/areas/" + areaId + "/inspections")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .file(new MockMultipartFile("photos", "foto.png", "image/png", TestImages.png()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.overallCondition").value("GOOD"))
            .andExpect(jsonPath("$.photos[0].takenAt").value("2026-05-10"))
            .andReturn().getResponse().getContentAsString();

        UUID inspectionId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        mockMvc.perform(get("/api/v1/areas/" + areaId + "/inspections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(inspectionId.toString()));
    }
}
