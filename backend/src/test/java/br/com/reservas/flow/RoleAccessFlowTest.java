package br.com.reservas.flow;

import static org.assertj.core.api.Assertions.assertThat;
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

/**
 * Fluxo de papeis (RN-01): SYNDIC e UNIT recebem 403 em rotas de ADMIN, UNIT recebe 403 em
 * fotos/vistorias de area, e qualquer rota autenticada sem token devolve 401 com ProblemDetail+code.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AreaTestStorageConfig.class, TestAreaReservationsGateway.Config.class})
@Transactional
class RoleAccessFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID condominiumId;
    private String syndicToken;
    private String unitToken;
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Papeis");

        String adminToken = createAccount("ADMIN", null);
        syndicToken = createAccount("SYNDIC", null);

        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into unit (id, condominium_id, block, number, identifier) values (?, ?, 'A', '1', 'a-1')",
            unitId, condominiumId);
        unitToken = createAccount("UNIT", unitId);

        areaId = createArea(adminToken);
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

    private UUID createArea(String adminToken) throws Exception {
        String dataJson = """
            {
              "name": "Salao %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 20,
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
    @DisplayName("RN-01: SYNDIC recebe 403 em GET /units (rota exclusiva de ADMIN)")
    void syndicForbiddenOnUnits() throws Exception {
        mockMvc.perform(get("/api/v1/units").header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-01: UNIT recebe 403 em GET /units (rota exclusiva de ADMIN)")
    void unitForbiddenOnUnits() throws Exception {
        mockMvc.perform(get("/api/v1/units").header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-01: SYNDIC recebe 403 em GET /admin/settings")
    void syndicForbiddenOnAdminSettings() throws Exception {
        mockMvc.perform(get("/api/v1/admin/settings").header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-01: UNIT recebe 403 em GET /admin/syndics")
    void unitForbiddenOnAdminSyndics() throws Exception {
        mockMvc.perform(get("/api/v1/admin/syndics").header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-20/RN-01: UNIT recebe 403 em GET /areas/{id}/photos")
    void unitForbiddenOnAreaPhotos() throws Exception {
        mockMvc.perform(get("/api/v1/areas/" + areaId + "/photos")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-20/RN-01: UNIT recebe 403 em GET /areas/{id}/inspections")
    void unitForbiddenOnAreaInspections() throws Exception {
        mockMvc.perform(get("/api/v1/areas/" + areaId + "/inspections")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-01: sem token, rota protegida devolve 401 com ProblemDetail contendo campo 'code'")
    void noTokenReturnsProblemDetailWithCode() throws Exception {
        String body = mockMvc.perform(get("/api/v1/units"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
            .andExpect(jsonPath("$.title").exists())
            .andExpect(jsonPath("$.status").value(401))
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("\tat ");
    }
}
