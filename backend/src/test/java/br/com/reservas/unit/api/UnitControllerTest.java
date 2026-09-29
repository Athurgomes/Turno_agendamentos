package br.com.reservas.unit.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
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

/**
 * `POST/GET/PUT/DELETE /units`, reset de senha e transferencia (RF-UNI-01..04,
 * RN-02, RN-03, RN-07, RN-08, RN-10, D-47).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UnitControllerTest extends AbstractIntegrationTest {

    private static final String ADMIN_PASSWORD = "SenhaForteAdmin1";

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

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Units");
        String adminLogin = "admin.units." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminLogin, passwordEncoder.encode(ADMIN_PASSWORD));
        adminToken = ApiLogin.token(mockMvc, objectMapper, adminLogin, ADMIN_PASSWORD);
    }

    private String createUnitPayload(String block, String number, String primaryCpf, String primaryEmail) {
        return """
            {
              "block": %s,
              "number": "%s",
              "primary": { "name": "Morador Principal", "phone": "5562999990000", "email": "%s", "cpf": "%s" },
              "members": []
            }
            """.formatted(block == null ? "null" : "\"" + block + "\"", number, primaryEmail, primaryCpf);
    }

    @Test
    @DisplayName("RN-02/RF-UNI-02: bloco A numero 1203 gera usuario a-1203 e a senha so aparece na resposta do cadastro")
    void createsUnitWithNormalizedUsernameAndShowsPasswordOnce() throws Exception {
        String response = mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitPayload("A", "1203", "12093506459", "morador1203@exemplo.test")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.unit.identifier").value("a-1203"))
            .andExpect(jsonPath("$.credentials.username").value("a-1203"))
            .andExpect(jsonPath("$.credentials.tempPassword").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        UUID unitId = UUID.fromString(json.get("unit").get("id").asText());

        mockMvc.perform(get("/api/v1/units/" + unitId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.residents[0].cpf").value("12093506459"))
            .andExpect(jsonPath("$.credentials").doesNotExist());
    }

    @Test
    @DisplayName("RN-02: identificador repetido -> 409 UNIT_IDENTIFIER_TAKEN")
    void duplicateIdentifierIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitPayload("B", "10", "20374611190", "b10@exemplo.test")))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitPayload("B", "10", "91678197378", "outro.b10@exemplo.test")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("UNIT_IDENTIFIER_TAKEN"));
    }

    @Test
    @DisplayName("RN-07: CPF com DV invalido no cadastro -> 422 CPF_INVALID")
    void invalidCpfIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitPayload("C", "20", "11111111111", "c20@exemplo.test")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CPF_INVALID"));
    }

    @Test
    @DisplayName("RN-08: mesmo CPF do principal e de um adicional na mesma unidade -> 422 DUPLICATE_CPF_IN_UNIT")
    void duplicateCpfInSameUnitIsRejected() throws Exception {
        String payload = """
            {
              "block": "D",
              "number": "30",
              "primary": { "name": "Principal", "phone": "5562999990000", "email": "d30@exemplo.test", "cpf": "01552021890" },
              "members": [ { "name": "Adicional", "phone": "5562999990001", "cpf": "01552021890" } ]
            }
            """;

        mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("DUPLICATE_CPF_IN_UNIT"));
    }

    @Test
    @DisplayName("RN-03: reset de senha gera nova senha temporaria (tempPassword=true) e revoga sessoes")
    void resetPasswordIssuesNewTemporaryPassword() throws Exception {
        UUID unitId = createUnit("E", "40", "00458475769", "e40@exemplo.test");

        mockMvc.perform(post("/api/v1/units/" + unitId + "/reset-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("e-40"))
            .andExpect(jsonPath("$.tempPassword").isNotEmpty());
    }

    @Test
    @DisplayName("RN-10: troca de titularidade gera nova senha, invalida o token antigo e devolve affectedReservations vazio")
    void transferInvalidatesOldTokenAndIssuesNewPassword() throws Exception {
        UUID unitId = createUnit("F", "50", "80517286530", "f50@exemplo.test");
        String unitToken = ApiLogin.token(mockMvc, objectMapper, "f-50", firstTempPassword);

        mockMvc.perform(get("/api/v1/me/unit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk());

        String transferPayload = """
            {
              "primary": { "name": "Novo Dono", "phone": "5562999991111", "email": "novo.f50@exemplo.test", "cpf": "67244999330" },
              "members": []
            }
            """;

        mockMvc.perform(post("/api/v1/units/" + unitId + "/transfer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.credentials.tempPassword").isNotEmpty())
            .andExpect(jsonPath("$.affectedReservations").isEmpty());

        mockMvc.perform(get("/api/v1/me/unit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D-47: desativar a unidade libera o identificador para um novo cadastro")
    void deactivatingUnitFreesIdentifierForRecreation() throws Exception {
        UUID unitId = createUnit("G", "60", "98672263995", "g60@exemplo.test");

        mockMvc.perform(delete("/api/v1/units/" + unitId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitPayload("G", "60", "12093506459", "novo.g60@exemplo.test")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.unit.identifier").value("g-60"));
    }

    @Test
    @DisplayName("RF-UNI-03: busca de unidade por nome de morador ignora acentos e maiusculas")
    void searchIgnoresAccentsAndCase() throws Exception {
        String payload = """
            {
              "block": "I",
              "number": "80",
              "primary": { "name": "José António", "phone": "5562999990000", "email": "jose.i80@exemplo.test", "cpf": "12093506459" },
              "members": []
            }
            """;
        mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/units").param("search", "jose antonio")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].identifier").value("i-80"))
            .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("F2 pendencia/F3: spring.data.web.pageable.max-page-size limita GET /units?size=5000 a 100")
    void pageSizeIsCappedAtConfiguredMax() throws Exception {
        mockMvc.perform(get("/api/v1/units").param("size", "5000")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    @DisplayName("RNF-11/docs-03: GET /units/{id} com id nao-UUID vira 400 VALIDATION_ERROR sem ecoar o valor bruto")
    void invalidUuidPathVariableReturnsValidationError() throws Exception {
        mockMvc.perform(get("/api/v1/units/not-a-uuid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors[0].field").value("id"))
            .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(
                org.hamcrest.Matchers.containsString("not-a-uuid"))));
    }

    @Test
    @DisplayName("RN-01: token SYNDIC recebe 403 em rota ADMIN de unidades")
    void syndicTokenIsForbiddenOnUnitAdminRoutes() throws Exception {
        String syndicLogin = "sindico.units." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'SYNDIC', ?, 'Sindico', ?)",
            condominiumId, syndicLogin, passwordEncoder.encode(ADMIN_PASSWORD));
        String syndicToken = ApiLogin.token(mockMvc, objectMapper, syndicLogin, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isForbidden());
    }

    private String firstTempPassword;

    private UUID createUnit(String block, String number, String cpf, String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createUnitPayload(block, number, cpf, email)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        firstTempPassword = json.get("credentials").get("tempPassword").asText();
        return UUID.fromString(json.get("unit").get("id").asText());
    }
}
