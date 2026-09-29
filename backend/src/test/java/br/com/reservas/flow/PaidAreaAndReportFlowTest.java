package br.com.reservas.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.TestImages;
import br.com.reservas.auth.support.MutableClock;
import br.com.reservas.reservation.support.ReservationFixtures;
import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
 * Etapa 2 (docs/09), fechamento F4/F5/F6: fluxo completo do morador em área
 * paga (RF-PAG, RN-25/RN-26/RN-31/RN-32) encadeado com abertura de report
 * (RF-REP, RN-34..37), mais os buracos de autorização (RN-01/D-16, IDOR) e de
 * visibilidade de dados sensíveis (D-55) apontados nas revisões da etapa que
 * não estavam cobertos, sem duplicar os cenários já verdes em
 * {@code PaymentControllerTest}, {@code ReportControllerTest},
 * {@code ReservationLifecycleControllerTest}, {@code BlockControllerTest} e
 * {@code FutureReservationsIntegrationTest} (citados em cada teste).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class PaidAreaAndReportFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 11);

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
    private UUID unitId;
    private UUID residentId;
    private String unitUsername;
    private String unitToken;
    private UUID paidAreaId;
    private UUID freeAreaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Fluxo Pagamento e Report");
        adminToken = createAdmin();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "A-101");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitUsername = "a-101";
        unitToken = createUnitAccount(unitId, unitUsername);
        paidAreaId = createArea(true, "150.00", "5562999998888");
        freeAreaId = createArea(false, null, null);
    }

    @Test
    @DisplayName("RF-PAG-01..04/RN-25/RN-26/RN-32: jornada completa do morador em area paga, do pedido "
        + "a confirmacao pelo ADMIN, e a reserva ja cancelada nao aceita confirmacao (409)")
    void fullPaidAreaJourney() throws Exception {
        // 1. UNIT reserva na area paga -> nasce PENDING_PAYMENT com whatsappPaymentUrl codificada.
        String createResponse = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .content(reservationPayload(paidAreaId, TOMORROW, "14:00", "15:00")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservation.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.whatsappPaymentUrl").value(org.hamcrest.Matchers.startsWith(
                "https://wa.me/5562999998888")))
            .andReturn().getResponse().getContentAsString();
        UUID reservationId = UUID.fromString(
            objectMapper.readTree(createResponse).get("reservation").get("id").asText());

        // 2. ADMIN ve a reserva em /payments/pending.
        mockMvc.perform(get("/api/v1/payments/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id=='" + reservationId + "')]").exists());

        // 3. ADMIN confirma o pagamento -> CONFIRMED.
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // 4. O morador ve CONFIRMED sem statusReason em /me/reservations.
        mockMvc.perform(get("/api/v1/me/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"))
            .andExpect(jsonPath("$.content[0].statusReason").doesNotExist());

        // 5. ADMIN tenta confirmar de novo -> 409.
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        // 6. Outra reserva pendente, ja cancelada -> confirmar -> 409 (CANCELLED -> CONFIRMED).
        String secondResponse = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .content(reservationPayload(paidAreaId, TOMORROW, "16:00", "17:00")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID secondId = UUID.fromString(objectMapper.readTree(secondResponse).get("reservation").get("id").asText());

        mockMvc.perform(post("/api/v1/reservations/" + secondId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"justification\": \"Area interditada pela vistoria\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"));

        mockMvc.perform(post("/api/v1/reservations/" + secondId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RN-31: pendente cujo inicio ja passou -> confirmar direto (sem listar antes) e 409, "
        + "e o motivo fica visivel ao morador")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void confirmingAnAlreadyExpiredPendingWithoutListingFirstIsRejected() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .content(reservationPayload(paidAreaId, TOMORROW, "09:00", "10:00")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID reservationId = UUID.fromString(
            objectMapper.readTree(createResponse).get("reservation").get("id").asText());

        advanceClockTo(TOMORROW, "09:01");
        String freshAdminToken = loginAdmin();
        String freshUnitToken = loginUnit();

        // Confirma direto, sem consultar /payments/pending nem /me/reservations antes:
        // a expiracao sob demanda (RN-31) tem que disparar dentro do proprio confirm-payment.
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshAdminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));

        mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshUnitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("CANCELLED"))
            .andExpect(jsonPath("$.content[0].statusReason")
                .value("Pagamento não confirmado até o início da reserva."));
    }

    @Test
    @DisplayName("D-16/vibe-security: conta UNIT recebe 403 em GET /payments/pending e "
        + "POST /reservations/{id}/confirm-payment")
    void unitCannotAccessPaymentRoutes() throws Exception {
        String createResponse = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .content(reservationPayload(paidAreaId, TOMORROW, "14:00", "15:00")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        UUID reservationId = UUID.fromString(
            objectMapper.readTree(createResponse).get("reservation").get("id").asText());

        mockMvc.perform(get("/api/v1/payments/pending")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/confirm-payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-01/vibe-security: conta UNIT de outra unidade recebe 403 FORBIDDEN_RESOURCE em "
        + "GET /reports/{id} e em POST /me/reservations/{id}/reports")
    void otherUnitCannotSeeOrCreateReportForSomeoneElsesReservation() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY(), "08:00", "09:00");
        UUID reportId = createReport(reservationId);

        UUID otherUnitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "B-202");
        UUID otherResidentId = ReservationFixtures.insertResident(jdbcTemplate, otherUnitId, "Bruno Lima");
        String otherUnitToken = createUnitAccount(otherUnitId, "b-202");

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUnitToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));

        String dataJson = """
            { "category": "DAMAGE", "description": "Tentativa indevida de report", "residentId": "%s" }
            """.formatted(otherResidentId);
        mockMvc.perform(multipart("/api/v1/me/reservations/" + reservationId + "/reports")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUnitToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    @DisplayName("D-55/vibe-security: residentPhone, whatsappContactUrl e maintenanceCost aparecem para "
        + "S/A em GET /reports/{id} e ficam ausentes na visao UNIT; custo negativo -> 400; "
        + "resolver com custo diminui o summary")
    void adminSeesContactAndCostFieldsHiddenFromResident() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY(), "08:00", "09:00");
        UUID reportId = createReport(reservationId);

        mockMvc.perform(get("/api/v1/reports/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(1));

        // Custo negativo -> 400 VALIDATION_ERROR (constraint estrutural do DTO, @DecimalMin).
        mockMvc.perform(patch("/api/v1/reports/" + reportId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"status\": \"RESOLVED\", \"maintenanceCost\": -10.00 }"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.residentPhone").exists())
            .andExpect(jsonPath("$.whatsappContactUrl").value("https://wa.me/5562999990000"));

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.residentPhone").doesNotExist())
            .andExpect(jsonPath("$.whatsappContactUrl").doesNotExist())
            .andExpect(jsonPath("$.maintenanceCost").doesNotExist());

        mockMvc.perform(patch("/api/v1/reports/" + reportId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"status\": \"RESOLVED\", \"maintenanceCost\": 80.00 }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maintenanceCost").value(80.00));

        mockMvc.perform(get("/api/v1/reports/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(0));
    }

    private LocalDate TODAY() {
        return LocalDate.ofInstant(clock.instant(), ZoneId.of("America/Sao_Paulo"));
    }

    private UUID createReport(UUID reservationId) throws Exception {
        String dataJson = """
            { "category": "DAMAGE", "description": "Churrasqueira com vazamento de gas", "residentId": "%s" }
            """.formatted(residentId);
        String response = mockMvc.perform(multipart("/api/v1/me/reservations/" + reservationId + "/reports")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // Insere a reserva direto no banco (mesmo padrao de ReportControllerTest): RN-20 proibiria
    // criar via API uma reserva "hoje" (SAME_DAY_NOT_ALLOWED), mas os cenarios de report/D-55
    // precisam de uma reserva CONFIRMED cujo dia ja comecou, sem depender de avancar o relogio.
    private UUID insertConfirmedReservation(LocalDate date, String startTime, String endTime) {
        UUID reservationId = UUID.randomUUID();
        UUID unitAccountId = jdbcTemplate.queryForObject(
            "select id from user_account where unit_id = ?", UUID.class, unitId);
        ZoneId zone = ZoneId.of("America/Sao_Paulo");
        java.time.Instant startAt = ZonedDateTime.of(date, LocalTime.parse(startTime), zone).toInstant();
        java.time.Instant endAt = ZonedDateTime.of(date, LocalTime.parse(endTime), zone).toInstant();
        String code = "RES-TEST-" + reservationId.toString().substring(0, 8);
        jdbcTemplate.update(
            "insert into reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id, "
                + "resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status, "
                + "requires_payment_snapshot, created_by, created_at) "
                + "values (?, ?, ?, ?, 'BOOKING', ?, ?, ?, ?, ?, ?, 2, 'CONFIRMED', false, ?, ?)",
            reservationId, code, condominiumId, freeAreaId, unitId, residentId, "Ana Souza", "5562999990000",
            java.sql.Timestamp.from(startAt), java.sql.Timestamp.from(endAt), unitAccountId,
            java.sql.Timestamp.from(clock.instant()));
        return reservationId;
    }

    private String reservationPayload(UUID areaId, LocalDate date, String startTime, String endTime) {
        return """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": 2 }
            """.formatted(areaId, date, startTime, endTime, residentId);
    }

    private void advanceClockTo(LocalDate date, String time) {
        ZonedDateTime target = ZonedDateTime.of(date, LocalTime.parse(time), ZoneId.of("America/Sao_Paulo"));
        Duration toAdvance = Duration.between(clock.instant(), target.toInstant());
        ((MutableClock) clock).advance(toAdvance);
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
              %s
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
            """.formatted(UUID.randomUUID(), requiresPayment,
            requiresPayment ? "\"price\": " + price + ", \"paymentWhatsapp\": \"" + whatsapp + "\"," : "");

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
        adminEmail = "admin.fluxo." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminEmail, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    private String loginAdmin() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    private String loginUnit() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, unitUsername, PASSWORD);
    }

    private String createUnitAccount(UUID unitId, String username) throws Exception {
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', ?, ?, ?)",
            condominiumId, username, unitId, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, username, PASSWORD);
    }
}
