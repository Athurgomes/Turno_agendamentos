package br.com.reservas.unit.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

/** `/me/unit` (RF-UNI-05, RN-09, RNF-01): auto-atendimento e protecao contra IDOR (RN-01). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MyUnitControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteUnidade1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID condominiumId;
    private UUID unitAId;
    private UUID primaryResidentAId;
    private String tokenUnitA;
    private String tokenUnitB;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio MyUnit");

        unitAId = insertUnit("h-70", "H", "70");
        primaryResidentAId = insertResident(unitAId, "Principal A", "12093506459", "principal.a@exemplo.test", true);
        createUnitAccount(unitAId, "h-70");
        tokenUnitA = ApiLogin.token(mockMvc, objectMapper, "h-70", PASSWORD);

        UUID unitBId = insertUnit("h-71", "H", "71");
        insertResident(unitBId, "Principal B", "20374611190", "principal.b@exemplo.test", true);
        createUnitAccount(unitBId, "h-71");
        tokenUnitB = ApiLogin.token(mockMvc, objectMapper, "h-71", PASSWORD);
    }

    private UUID insertUnit(String identifier, String block, String number) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into unit (id, condominium_id, block, number, identifier) values (?,?,?,?,?)",
            id, condominiumId, block, number, identifier);
        return id;
    }

    private UUID insertResident(UUID unitId, String name, String cpf, String email, boolean primary) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into resident (id, unit_id, name, phone, email, cpf, is_primary) values (?,?,?,?,?,?,?)",
            id, unitId, name, "5562999990000", email, cpf, primary);
        return id;
    }

    private void createUnitAccount(UUID unitId, String username) {
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', ?, ?, ?)",
            condominiumId, username, unitId, passwordEncoder.encode(PASSWORD));
    }

    @Test
    @DisplayName("RNF-01: GET /me/unit devolve o CPF do morador mascarado")
    void getUnitReturnsMaskedCpf() throws Exception {
        mockMvc.perform(get("/api/v1/me/unit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUnitA))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("h-70"))
            .andExpect(jsonPath("$.residents[0].cpf").value("***.935.064-**"));
    }

    @Test
    @DisplayName("RN-09: conta UNIT adiciona morador adicional (primary=false)")
    void addsAdditionalResident() throws Exception {
        String payload = """
            { "name": "Adicional A", "phone": "5562999990002", "email": "adicional.a@exemplo.test" }
            """;

        mockMvc.perform(post("/api/v1/me/unit/residents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUnitA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.primary").value(false));
    }

    @Test
    @DisplayName("RN-09: remover o morador principal -> 422 CANNOT_REMOVE_PRIMARY")
    void cannotRemovePrimaryResident() throws Exception {
        mockMvc.perform(delete("/api/v1/me/unit/residents/" + primaryResidentAId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUnitA))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_PRIMARY"));
    }

    @Test
    @DisplayName("RN-01 (IDOR): conta UNIT nao edita morador de outra unidade -> 403 FORBIDDEN_RESOURCE")
    void cannotEditResidentFromAnotherUnit() throws Exception {
        String payload = """
            { "name": "Tentativa de Ataque", "phone": "5562999990003", "email": "ataque@exemplo.test" }
            """;

        mockMvc.perform(put("/api/v1/me/unit/residents/" + primaryResidentAId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUnitB)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    @DisplayName("RN-01 (IDOR): conta UNIT nao remove morador de outra unidade -> 403 FORBIDDEN_RESOURCE")
    void cannotRemoveResidentFromAnotherUnit() throws Exception {
        mockMvc.perform(delete("/api/v1/me/unit/residents/" + primaryResidentAId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUnitB))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    @DisplayName("RN-09: PUT ignora qualquer CPF enviado (campo nem existe no request de edicao)")
    void updateResidentNeverChangesCpf() throws Exception {
        String payload = """
            { "name": "Principal A Editado", "phone": "5562999990009", "email": "editado.a@exemplo.test" }
            """;

        mockMvc.perform(put("/api/v1/me/unit/residents/" + primaryResidentAId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenUnitA)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Principal A Editado"))
            .andExpect(jsonPath("$.cpf").value("***.935.064-**"));
    }
}
