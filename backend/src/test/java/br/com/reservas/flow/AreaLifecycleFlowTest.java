package br.com.reservas.flow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fluxo de vida de uma area comum (F3, RF-ARE-01..09, RN-11..17, D-20, D-44) e, ao final,
 * confirmacao de que a troca de titularidade de uma unidade invalida o token antigo (RN-10).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AreaTestStorageConfig.class, TestAreaReservationsGateway.Config.class})
@Transactional
class AreaLifecycleFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TestAreaReservationsGateway reservationsGateway;

    private UUID condominiumId;
    private String adminToken;
    private String syndicToken;
    private String unitToken;

    @BeforeEach
    void setUp() throws Exception {
        reservationsGateway.setFuture(java.util.List.of());
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Areas Flow");

        adminToken = createAccount("ADMIN", null);
        syndicToken = createAccount("SYNDIC", null);

        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into unit (id, condominium_id, block, number, identifier) values (?, ?, 'A', '1', 'a-1')",
            unitId, condominiumId);
        unitToken = createAccount("UNIT", unitId);
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

    @Test
    @DisplayName("RN-12: ADMIN cadastra area paga sem valor/WhatsApp -> 422 PAYMENT_INFO_REQUIRED")
    void payingAreaWithoutPaymentInfoIsRejected() throws Exception {
        String payload = """
            {
              "name": "Salao Pago %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 50,
              "requiresPayment": true,
              "openingHours": [ { "dayOfWeek": 6, "openTime": "10:00", "closeTime": "22:00" } ]
            }
            """.formatted(UUID.randomUUID());

        mockMvc.perform(multipart("/api/v1/areas")
                .file(new MockMultipartFile("data", "data", "application/json", payload.getBytes()))
                .file(new MockMultipartFile("photos", "a.png", "image/png", TestImages.png()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("PAYMENT_INFO_REQUIRED"));
    }

    @Test
    @DisplayName("RF-ARE-01/04/05/RN-14..17/D-44: ciclo completo de uma area gratuita, do cadastro a manutencao")
    void fullAreaLifecycle() throws Exception {
        // 1. ADMIN cadastra area gratuita com foto.
        String dataJson = """
            {
              "name": "Churrasqueira Fluxo %s",
              "category": "BARBECUE",
              "description": "Descricao original",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 20,
              "requiresPayment": false,
              "openingHours": [ { "dayOfWeek": 6, "openTime": "10:00", "closeTime": "22:00" } ]
            }
            """.formatted(UUID.randomUUID());
        String createResponse = mockMvc.perform(multipart("/api/v1/areas")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .file(new MockMultipartFile("photos", "a.png", "image/png", TestImages.png()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn().getResponse().getContentAsString();
        UUID areaId = UUID.fromString(objectMapper.readTree(createResponse).get("id").asText());

        // 2. UNIT ve a area no catalogo com coverPhotoUrl e uploadedBy nulo no detalhe.
        mockMvc.perform(get("/api/v1/areas").header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + areaId + "')].coverPhotoUrl").exists());

        mockMvc.perform(get("/api/v1/areas/" + areaId).header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.photos[0].uploadedBy").value(org.hamcrest.Matchers.nullValue()));

        // 3. SYNDIC edita a descricao (permitido) mas nao muda status nem preco (D-20).
        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"description\": \"Descricao revisada pelo sindico\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Descricao revisada pelo sindico"));

        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"MAINTENANCE\" }"))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"requiresPayment\": true, \"price\": 100, \"paymentWhatsapp\": \"5562999990000\" }"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // 4. ADMIN poe a area em manutencao sem reservas futuras afetadas.
        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"MAINTENANCE\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.area.status").value("MAINTENANCE"))
            .andExpect(jsonPath("$.cancelledReservations").value(0));

        // 5. Upload de conteudo que nao e imagem, com extensao de imagem -> 422 INVALID_FILE.
        mockMvc.perform(multipart("/api/v1/areas/" + areaId + "/photos")
                .file(new MockMultipartFile("photos", "foto.jpg", "image/jpeg",
                    TestImages.fakePdfWithJpgExtension()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("RN-10: troca de titularidade de unidade invalida o token antigo (401)")
    void unitOwnershipTransferInvalidatesOldToken() throws Exception {
        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into unit (id, condominium_id, block, number, identifier) values (?, ?, 'B', '2', 'b-2')",
            unitId, condominiumId);
        jdbcTemplate.update(
            "insert into resident (id, unit_id, name, phone, email, cpf, is_primary) "
                + "values (?, ?, 'Original', '5562999990000', 'original.b2@exemplo.test', '67244999330', true)",
            UUID.randomUUID(), unitId);
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', 'b-2', ?, ?)",
            condominiumId, unitId, passwordEncoder.encode(PASSWORD));
        String oldToken = ApiLogin.token(mockMvc, objectMapper, "b-2", PASSWORD);

        mockMvc.perform(get("/api/v1/me/unit").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
            .andExpect(status().isOk());

        String transferPayload = """
            {
              "primary": { "name": "Novo Dono", "phone": "5562999991111", "email": "novo.b2@exemplo.test",
                "cpf": "80517286530" },
              "members": []
            }
            """;
        mockMvc.perform(post("/api/v1/units/" + unitId + "/transfer")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transferPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.credentials.tempPassword").isNotEmpty());

        mockMvc.perform(get("/api/v1/me/unit").header(HttpHeaders.AUTHORIZATION, "Bearer " + oldToken))
            .andExpect(status().isUnauthorized());
    }
}
