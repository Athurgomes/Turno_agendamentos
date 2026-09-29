package br.com.reservas.settings.api;

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

/** `/admin/syndics` (RF-UNI-07): criacao com senha temporaria, e-mail unico, desativacao. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SyndicControllerTest extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Syndics");
        String adminLogin = "admin.syndics." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminLogin, passwordEncoder.encode(PASSWORD));
        adminToken = ApiLogin.token(mockMvc, objectMapper, adminLogin, PASSWORD);
    }

    @Test
    @DisplayName("RF-UNI-07: ADMIN cria conta de sindico com senha temporaria (username = e-mail)")
    void createsSyndicAccountWithTemporaryPassword() throws Exception {
        String payload = """
            { "name": "Sindico Novo", "email": "sindico.novo@exemplo.test", "phone": "5562999998888" }
            """;

        String response = mockMvc.perform(post("/api/v1/admin/syndics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.syndic.email").value("sindico.novo@exemplo.test"))
            .andExpect(jsonPath("$.credentials.username").value("sindico.novo@exemplo.test"))
            .andExpect(jsonPath("$.credentials.tempPassword").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String syndicId = json.get("syndic").get("id").asText();

        mockMvc.perform(get("/api/v1/admin/syndics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + syndicId + "')]").exists());
    }

    @Test
    @DisplayName("RF-UNI-07: e-mail ja usado -> 409 EMAIL_TAKEN")
    void duplicateEmailIsRejected() throws Exception {
        String payload = """
            { "name": "Sindico Um", "email": "duplicado@exemplo.test", "phone": "5562999998888" }
            """;
        mockMvc.perform(post("/api/v1/admin/syndics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/syndics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    @DisplayName("RN-06: desativar a conta de sindico invalida as sessoes ativas")
    void deactivatingSyndicRevokesSessions() throws Exception {
        String payload = """
            { "name": "Sindico Desativar", "email": "sindico.desativar@exemplo.test", "phone": "5562999998888" }
            """;
        String response = mockMvc.perform(post("/api/v1/admin/syndics")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        String syndicId = json.get("syndic").get("id").asText();
        String syndicToken = ApiLogin.token(mockMvc, objectMapper, "sindico.desativar@exemplo.test",
            json.get("credentials").get("tempPassword").asText());

        mockMvc.perform(delete("/api/v1/admin/syndics/" + syndicId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isUnauthorized());
    }
}
