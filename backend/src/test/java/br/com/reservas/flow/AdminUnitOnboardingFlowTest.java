package br.com.reservas.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.auth.domain.TemporaryPasswordGenerator;
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
 * Fluxo de ponta a ponta ADMIN -> UNIT (F2, RF-UNI-01..05, RN-02..04, RN-09, RN-01/IDOR):
 * ADMIN cadastra a unidade e recebe as credenciais uma unica vez; a conta da unidade
 * loga com a senha temporaria, troca a senha, mexe nos moradores e nao alcanca dados
 * de outra unidade.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminUnitOnboardingFlowTest extends AbstractIntegrationTest {

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
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Fluxo");
        String adminLogin = "admin.flow." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminLogin, passwordEncoder.encode(ADMIN_PASSWORD));
        adminToken = ApiLogin.token(mockMvc, objectMapper, adminLogin, ADMIN_PASSWORD);
    }

    @Test
    @DisplayName("RF-UNI-01..05/RN-02/RN-03/RN-04/RN-09/RN-01: ADMIN cria bloco A 1203, UNIT loga, troca senha,"
        + " adiciona morador, nao remove o principal e nao mexe em outra unidade (IDOR)")
    void fullOnboardingFlow() throws Exception {
        // 1. ADMIN cria a unidade bloco A numero 1203.
        String createPayload = """
            {
              "block": "A",
              "number": "1203",
              "primary": { "name": "Morador Principal", "phone": "5562999990000",
                "email": "principal.1203@exemplo.test", "cpf": "12093506459" },
              "members": []
            }
            """;
        String createResponse = mockMvc.perform(post("/api/v1/units")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(createPayload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.unit.identifier").value("a-1203"))
            .andExpect(jsonPath("$.credentials.username").value("a-1203"))
            .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        UUID unitId = UUID.fromString(created.get("unit").get("id").asText());
        UUID primaryResidentId = jdbcTemplate.queryForObject(
            "select id from resident where unit_id = ? and is_primary = true", UUID.class, unitId);
        String tempPassword = created.get("credentials").get("tempPassword").asText();

        // RN-03: senha temporaria tem 6 caracteres, todos do alfabeto sem ambiguidade visual.
        assertThat(tempPassword).hasSize(6);
        assertThat(tempPassword.chars().allMatch(c -> TemporaryPasswordGenerator.ALPHABET.indexOf(c) >= 0)).isTrue();

        // 2. UNIT loga com a senha temporaria -> tempPassword=true.
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "198.51.100.60.1")
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("login", "a-1203", "password", tempPassword))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.tempPassword").value(true))
            .andReturn().getResponse().getContentAsString();
        String unitToken = objectMapper.readTree(loginResponse).get("accessToken").asText();

        // 3. UNIT troca a senha (RN-04) -> tempPassword some do /auth/me.
        String newPassword = "NovaSenhaForte123";
        mockMvc.perform(put("/api/v1/auth/password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    java.util.Map.of("currentPassword", tempPassword, "newPassword", newPassword))))
            .andExpect(status().isNoContent());

        // a troca de senha revoga os refresh tokens da conta, mas o access token em memoria
        // continua valido ate expirar; loga de novo para simular a sessao pos-troca.
        String unitTokenAfterChange = ApiLogin.token(mockMvc, objectMapper, "a-1203", newPassword);
        mockMvc.perform(post("/api/v1/me/unit/residents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitTokenAfterChange)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Morador Adicional", "phone": "5562999990002", "email": "adicional.1203@exemplo.test" }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.primary").value(false));

        // 4. RN-09: nao remove o morador principal.
        mockMvc.perform(delete("/api/v1/me/unit/residents/" + primaryResidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitTokenAfterChange))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CANNOT_REMOVE_PRIMARY"));

        // 5. RN-01 (IDOR): outra unidade nao edita o morador da unidade 1203.
        UUID otherUnitId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into unit (id, condominium_id, block, number, identifier) values (?, ?, 'A', '1204', 'a-1204')",
            otherUnitId, condominiumId);
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', 'a-1204', ?, ?)",
            condominiumId, otherUnitId, passwordEncoder.encode("SenhaForteVizinho1"));
        String otherUnitToken = ApiLogin.token(mockMvc, objectMapper, "a-1204", "SenhaForteVizinho1");

        mockMvc.perform(put("/api/v1/me/unit/residents/" + primaryResidentId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUnitToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    { "name": "Ataque", "phone": "5562999990009", "email": "ataque@exemplo.test" }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));
    }
}
