package br.com.reservas.area.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.TestAreaReservationsGateway;
import br.com.reservas.area.support.AreaTestStorageConfig;
import br.com.reservas.area.support.TestImages;
import br.com.reservas.shared.reservation.ReservationSummary;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * `POST/GET/PUT/PATCH/DELETE /areas` (RF-ARE-01,02,05,09; RN-11..16; D-20, D-44).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AreaTestStorageConfig.class, TestAreaReservationsGateway.Config.class})
@Transactional
class AreaControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteAdmin1";

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
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Areas");

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

    private String createAreaPayload(boolean requiresPayment, String price, String whatsapp) {
        return """
            {
              "name": "Salao %s",
              "category": "PARTY_ROOM",
              "description": "Descricao do salao",
              "rules": "Regras do salao",
              "conductGuidelines": "Sugestoes de conduta",
              "capacity": 50,
              "requiresPayment": %s,
              "price": %s,
              "paymentWhatsapp": %s,
              "openingHours": [ { "dayOfWeek": 6, "openTime": "10:00", "closeTime": "22:00" } ]
            }
            """.formatted(UUID.randomUUID(), requiresPayment, price == null ? "null" : price,
            whatsapp == null ? "null" : "\"" + whatsapp + "\"");
    }

    private org.springframework.test.web.servlet.ResultActions postArea(String token, String dataJson, byte[]... photos)
        throws Exception {
        var request = multipart("/api/v1/areas")
            .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()));
        for (int i = 0; i < photos.length; i++) {
            request = request.file(new MockMultipartFile("photos", "foto" + i + ".png", "image/png", photos[i]));
        }
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    @Test
    @DisplayName("RN-12: area paga sem valor e sem WhatsApp -> 422 PAYMENT_INFO_REQUIRED")
    void payingAreaWithoutPriceOrWhatsappIsRejected() throws Exception {
        postArea(adminToken, createAreaPayload(true, null, null), TestImages.png())
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("PAYMENT_INFO_REQUIRED"));
    }

    @Test
    @DisplayName("RN-11: cadastro sem foto -> 422 INVALID_FILE")
    void createWithoutPhotoIsRejected() throws Exception {
        postArea(adminToken, createAreaPayload(false, null, null))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("RN-11/RF-ARE-01: ADMIN cadastra area gratuita com foto e horario")
    void adminCreatesFreeAreaWithPhotoAndOpeningHours() throws Exception {
        postArea(adminToken, createAreaPayload(false, null, null), TestImages.png())
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.photos[0].url").isNotEmpty())
            .andExpect(jsonPath("$.openingHours[0].dayOfWeek").value(6));
    }

    @Test
    @DisplayName("RN-13: GET /area-categories devolve template de regras e conduta para cada categoria")
    void categoriesHaveTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/area-categories").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(15))
            .andExpect(jsonPath("$[0].rulesTemplate").value(
                org.hamcrest.Matchers.containsString("Convenção e o Regimento Interno")))
            .andExpect(jsonPath("$[0].conductTemplate").value(
                org.hamcrest.Matchers.containsString("Convenção e o Regimento Interno")));
    }

    @Test
    @DisplayName("D-20/RN-14: SYNDIC recebe 403 ao tentar mudar status")
    void syndicCannotChangeStatus() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"MAINTENANCE\" }"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-20: SYNDIC recebe 403 ao tentar alterar valor/cobranca pelo PUT")
    void syndicCannotChangePayment() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"requiresPayment\": true, \"price\": 100, \"paymentWhatsapp\": \"5562999990000\" }"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("D-20: SYNDIC recebe 403 ao tentar alterar horario pelo PUT")
    void syndicCannotChangeOpeningHours() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"openingHours\": [ { \"dayOfWeek\": 1, \"openTime\": \"08:00\", \"closeTime\": \"12:00\" } ] }"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-20: SYNDIC recebe 403 mesmo enviando campo restrito com o valor ja atual")
    void syndicCannotSendAdminOnlyFieldEvenWithCurrentValue() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"requiresPayment\": false }"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("D-44: GET /areas/{id} devolve openTime/closeTime no formato 'HH:mm'")
    void detailReturnsOpeningHoursInHourMinuteFormat() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(get("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openingHours[0].openTime").value("10:00"))
            .andExpect(jsonPath("$.openingHours[0].closeTime").value("22:00"));
    }

    @Test
    @DisplayName("D-44: PUT /areas/{id} aceita openTime com segundos ('HH:mm:ss')")
    void updateAcceptsOpeningHoursWithSeconds() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(
                    "{ \"openingHours\": [ { \"dayOfWeek\": 1, \"openTime\": \"08:00:00\", \"closeTime\": \"12:00:00\" } ] }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.openingHours[0].openTime").value("08:00"))
            .andExpect(jsonPath("$.openingHours[0].closeTime").value("12:00"));
    }

    @Test
    @DisplayName("RF-ARE-05/D-20: SYNDIC edita descricao e capacidade com sucesso")
    void syndicEditsDescriptiveFieldsSuccessfully() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"description\": \"Nova descricao\", \"capacity\": 30 }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.description").value("Nova descricao"))
            .andExpect(jsonPath("$.capacity").value(30));
    }

    @Test
    @DisplayName("D-44: PUT com version desatualizada -> 409 CONFLICT")
    void updateWithStaleVersionIsRejected() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(put("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"description\": \"x\", \"version\": 99 }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("RN-16: mudar status com reservas futuras ativas e sem confirmar -> 409 AREA_HAS_FUTURE_RESERVATIONS")
    void statusChangeWithAffectedReservationsWithoutConfirmationIsRejected() throws Exception {
        UUID areaId = createArea();
        reservationsGateway.setFuture(java.util.List.of(new ReservationSummary(UUID.randomUUID(), "RES-2026-000001",
            "BOOKING", areaId, "Salao", "A-1", "Morador", LocalDate.now().plusDays(1), LocalTime.of(10, 0),
            LocalTime.of(12, 0), "CONFIRMED")));

        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"MAINTENANCE\" }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AREA_HAS_FUTURE_RESERVATIONS"))
            .andExpect(jsonPath("$.affectedReservations.length()").value(1));
    }

    @Test
    @DisplayName("RN-16: com confirmacao e justificativa, cancela em lote e muda o status -> 200")
    void statusChangeWithConfirmationCancelsAffectedReservations() throws Exception {
        UUID areaId = createArea();
        reservationsGateway.setFuture(java.util.List.of(new ReservationSummary(UUID.randomUUID(), "RES-2026-000002",
            "BOOKING", areaId, "Salao", "A-1", "Morador", LocalDate.now().plusDays(1), LocalTime.of(10, 0),
            LocalTime.of(12, 0), "CONFIRMED")));

        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{ \"status\": \"MAINTENANCE\", \"justification\": \"Reforma geral do espaco\", "
                    + "\"confirmCancelAffected\": true }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.area.status").value("MAINTENANCE"))
            .andExpect(jsonPath("$.cancelledReservations").value(1));
    }

    @Test
    @DisplayName("RN-15: SYNDIC recebe 403 ao tentar excluir area")
    void syndicCannotDeleteArea() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(delete("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-15: ADMIN exclui area sem reservas futuras -> 204 e some do catalogo")
    void adminDeletesAreaWithoutFutureReservations() throws Exception {
        UUID areaId = createArea();
        mockMvc.perform(delete("/api/v1/areas/" + areaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/areas").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + areaId + "')]").isEmpty());
    }

    @Test
    @DisplayName("RF-ARE-04: UNIT enxerga o catalogo com coverPhotoUrl")
    void unitSeesCatalogWithCoverPhoto() throws Exception {
        createArea();
        mockMvc.perform(get("/api/v1/areas").header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].coverPhotoUrl").isNotEmpty());
    }

    private UUID createArea() throws Exception {
        String response = postArea(adminToken, createAreaPayload(false, null, null), TestImages.png())
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }
}
