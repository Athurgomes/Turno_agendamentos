package br.com.reservas.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.TestImages;
import br.com.reservas.auth.support.MutableClock;
import br.com.reservas.reservation.support.ReservationFixtures;
import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * F5-1 (RF-PAG-02..04): `GET /payments/pending`, `POST /reservations/{id}/confirm-payment` e a
 * expiração sob demanda (RN-31) disparada por `GET /me/reservations`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class PaymentControllerTest extends AbstractIntegrationTest {

    private static final LocalDate DAY_AFTER_TOMORROW = LocalDate.of(2026, 11, 12);
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 11);
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
    private Clock clock;

    private UUID condominiumId;
    private String adminEmail;
    private String adminToken;
    private String syndicToken;
    private UUID unitId;
    private UUID residentId;
    private String unitUsername;
    private String unitToken;
    private UUID paidAreaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Pagamentos");
        adminToken = createAdmin();
        syndicToken = createSyndic();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "A-101");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitUsername = "a-101";
        unitToken = createUnitAccount(unitId, unitUsername);
        paidAreaId = createArea(true, "150.00", "5562999998888");
    }

    @Test
    @DisplayName("RF-PAG-02: GET /payments/pending ordena por inicio crescente com within48h correto na borda")
    void pendingListedAscendingWithWithin48hAtTheBoundary() throws Exception {
        // now + 48h exatos: dentro do prazo (<=).
        UUID atBoundary = createPendingReservation(unitToken, DAY_AFTER_TOMORROW, "10:00", "11:00", residentId);
        // now + 49h: fora do prazo (sem sobrepor o slot anterior, RN-24).
        UUID pastBoundary = createPendingReservation(unitToken, DAY_AFTER_TOMORROW, "11:00", "12:00", residentId);
        // Mais cedo que os dois -> primeiro na lista.
        UUID earliest = createPendingReservation(unitToken, TOMORROW, "09:00", "10:00", residentId);

        String response = mockMvc.perform(get("/api/v1/payments/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(response);
        assertThat(content).hasSize(3);
        assertThat(content.get(0).get("id").asText()).isEqualTo(earliest.toString());
        assertThat(content.get(1).get("id").asText()).isEqualTo(atBoundary.toString());
        assertThat(content.get(1).get("within48h").asBoolean()).isTrue();
        assertThat(content.get(2).get("id").asText()).isEqualTo(pastBoundary.toString());
        assertThat(content.get(2).get("within48h").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("RF-PAG-03/RN-32: ADMIN confirma pendente -> CONFIRMED, sem statusReason em /me/reservations")
    void adminConfirmsPendingPayment() throws Exception {
        UUID reservationId = createPendingReservation(unitToken, TOMORROW, "14:00", "15:00", residentId);

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.paymentConfirmedAt").exists());

        mockMvc.perform(get("/api/v1/me/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
            .andExpect(jsonPath("$.content[0].statusReason").doesNotExist());
    }

    @Test
    @DisplayName("RN-32: confirmar pagamento de reserva ja CANCELLED -> 409 INVALID_STATUS_TRANSITION")
    void confirmingCancelledReservationIsRejected() throws Exception {
        UUID reservationId = createPendingReservation(unitToken, TOMORROW, "14:00", "15:00", residentId);
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"justification\": \"Area em manutencao urgente\" }"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RN-32: confirmar pagamento de reserva ja CONFIRMED -> 409 INVALID_STATUS_TRANSITION")
    void confirmingAlreadyConfirmedReservationIsRejected() throws Exception {
        UUID reservationId = createPendingReservation(unitToken, TOMORROW, "14:00", "15:00", residentId);
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RF-RES-08/RN-27/RN-29: ADMIN cancela pendente com justificativa e o morador ve o motivo")
    void adminCancelsPendingAndResidentSeesReason() throws Exception {
        UUID reservationId = createPendingReservation(unitToken, TOMORROW, "14:00", "15:00", residentId);

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"justification\": \"Area interditada pela vistoria\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(get("/api/v1/me/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].statusReason").value("Area interditada pela vistoria"));
    }

    @Test
    @DisplayName("D-16/vibe-security: SYNDIC nao acessa /payments/pending nem confirma pagamento (403)")
    void syndicCannotAccessPaymentsRoutes() throws Exception {
        UUID reservationId = createPendingReservation(unitToken, TOMORROW, "14:00", "15:00", residentId);

        mockMvc.perform(get("/api/v1/payments/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-31: pendente com inicio ja passado expira ao consultar /me/reservations")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void expiredPendingBecomesCancelledOnDemand() throws Exception {
        UUID reservationId = createPendingReservation(unitToken, TOMORROW, "09:00", "10:00", residentId);
        advanceClockTo(TOMORROW, "09:01");
        String freshUnitToken = loginUnit();
        String freshAdminToken = loginAdmin();

        mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshUnitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.content[0].cancelledBy").value("SYSTEM"))
            .andExpect(jsonPath("$.content[0].statusReason")
                .value("Pagamento não confirmado até o início da reserva."));

        String eventsResponse = mockMvc.perform(get("/api/v1/reservations/" + reservationId + "/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshAdminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode events = objectMapper.readTree(eventsResponse);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).get("type").asText()).isEqualTo("EXPIRED");
        assertThat(events.get(1).get("actor").isNull()).isTrue();
    }

    @Test
    @DisplayName("RN-31: duas consultas seguidas nao duplicam o evento EXPIRED")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void expirationIsIdempotentAcrossConsecutiveQueries() throws Exception {
        UUID reservationId = createPendingReservation(unitToken, TOMORROW, "09:00", "10:00", residentId);
        advanceClockTo(TOMORROW, "09:01");
        String freshUnitToken = loginUnit();
        String freshAdminToken = loginAdmin();

        mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshUnitToken))
            .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshUnitToken))
            .andExpect(status().isOk());

        String eventsResponse = mockMvc.perform(get("/api/v1/reservations/" + reservationId + "/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshAdminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode events = objectMapper.readTree(eventsResponse);
        long expiredCount = 0;
        for (JsonNode e : events) {
            if ("EXPIRED".equals(e.get("type").asText())) {
                expiredCount++;
            }
        }
        assertThat(expiredCount).isEqualTo(1);
    }

    private void advanceClockTo(LocalDate date, String time) {
        var target = java.time.ZonedDateTime.of(date, java.time.LocalTime.parse(time),
            java.time.ZoneId.of("America/Sao_Paulo")).toInstant();
        Duration toAdvance = Duration.between(clock.instant(), target);
        ((MutableClock) clock).advance(toAdvance);
    }

    private UUID createPendingReservation(String token, LocalDate date, String startTime, String endTime,
        UUID residentId) throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": 2 }
            """.formatted(paidAreaId, date, startTime, endTime, residentId);
        String response = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservation.status").value("PENDING_PAYMENT"))
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("reservation").get("id").asText());
    }

    private UUID createArea(boolean requiresPayment, String price, String whatsapp) throws Exception {
        String payload = """
            {
              "name": "Area %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 20,
              "requiresPayment": %s,
              "price": %s,
              "paymentWhatsapp": "%s",
              "openingHours": [
                { "dayOfWeek": 1, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 2, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 3, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 4, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 5, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 6, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 7, "openTime": "00:00", "closeTime": "23:30" }
              ]
            }
            """.formatted(UUID.randomUUID(), requiresPayment, price, whatsapp);

        var request = multipart("/api/v1/areas")
            .file(new MockMultipartFile("data", "data", "application/json", payload.getBytes()))
            .file(new MockMultipartFile("photos", "foto.png", "image/png", TestImages.png()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        String response = mockMvc.perform(request)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String createAdmin() throws Exception {
        adminEmail = "admin.pagamentos." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminEmail, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    // RN-06: um `Bearer` emitido antes de {@link #advanceClockTo} expira (TTL de 15 min, `exp`
    // validado pelo mesmo Clock de teste) assim que o relogio avanca mais de um dia; refaz o
    // login com o mesmo usuario para continuar chamando a API depois do avanco.
    private String loginAdmin() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    private String loginUnit() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, unitUsername, PASSWORD);
    }

    private String createSyndic() throws Exception {
        String email = "sindico.pagamentos." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'SYNDIC', ?, 'Sindico', ?)",
            condominiumId, email, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, email, PASSWORD);
    }

    private String createUnitAccount(UUID unitId, String username) throws Exception {
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', ?, ?, ?)",
            condominiumId, username, unitId, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, username, PASSWORD);
    }
}
