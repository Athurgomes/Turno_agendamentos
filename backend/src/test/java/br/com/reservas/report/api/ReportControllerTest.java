package br.com.reservas.report.api;

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
import com.fasterxml.jackson.databind.JsonNode;
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
 * F6-2/F6-3 (RF-REP-01..05, RN-01, RN-17, RN-34..37, D-50, D-55). "Agora" =
 * terça 2026-11-10 10:00 em America/Sao_Paulo ({@link ReservationTestConfig}).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class ReportControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";
    private static final LocalDate TOMORROW = LocalDate.of(2026, 11, 11);
    // "Agora" (ReservationTestConfig) e terca 2026-11-10 10:00 America/Sao_Paulo: uma reserva
    // hoje as 08:00 ja aconteceu, dentro da janela RN-34 sem precisar avancar o relogio (e sem
    // invalidar os tokens ja emitidos, TTL de 15 min).
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

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
    private String syndicEmail;
    private String syndicToken;
    private UUID unitId;
    private UUID residentId;
    private String unitUsername;
    private String unitToken;
    private UUID otherUnitId;
    private UUID otherResidentId;
    private String otherUnitToken;
    private UUID freeAreaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Reports");
        adminToken = createAdmin();
        syndicToken = createSyndic();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "a-1");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitUsername = "a-1";
        unitToken = createUnitAccount(unitId, unitUsername);
        otherUnitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "b-2");
        otherResidentId = ReservationFixtures.insertResident(jdbcTemplate, otherUnitId, "Bruno Lima");
        otherUnitToken = createUnitAccount(otherUnitId, "b-2");
        freeAreaId = createArea();
    }

    @Test
    @DisplayName("RN-34: D-1 23:59 (dia anterior ao da reserva) -> 422 REPORT_WINDOW_CLOSED")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void dayBeforeReservationIsClosed() throws Exception {
        UUID reservationId = createConfirmedReservation(unitToken, TOMORROW, "14:00", "15:00");
        advanceClockTo(TOMORROW.minusDays(1), "23:59");
        String freshUnitToken = loginUnit();

        createReport(freshUnitToken, reservationId, new byte[0][])
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REPORT_WINDOW_CLOSED"));
    }

    @Test
    @DisplayName("RN-34: D 00:00 (dia da reserva) -> janela aberta, report criado")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void reservationDayIsOpen() throws Exception {
        UUID reservationId = createConfirmedReservation(unitToken, TOMORROW, "14:00", "15:00");
        advanceClockTo(TOMORROW, "00:00");
        String freshUnitToken = loginUnit();

        createReport(freshUnitToken, reservationId, new byte[0][])
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.startsWith("OCR-")));
    }

    @Test
    @DisplayName("RN-34: D+7 23:59:59 (ultimo instante da janela) -> ainda aberta")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void lastDayOfWindowIsOpen() throws Exception {
        UUID reservationId = createConfirmedReservation(unitToken, TOMORROW, "14:00", "15:00");
        advanceClockTo(TOMORROW.plusDays(7), "23:59");
        String freshUnitToken = loginUnit();

        createReport(freshUnitToken, reservationId, new byte[0][])
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RN-34: D+8 00:00 (um dia depois do fim da janela) -> 422 REPORT_WINDOW_CLOSED")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void dayAfterWindowIsClosed() throws Exception {
        UUID reservationId = createConfirmedReservation(unitToken, TOMORROW, "14:00", "15:00");
        advanceClockTo(TOMORROW.plusDays(8), "00:00");
        String freshUnitToken = loginUnit();

        createReport(freshUnitToken, reservationId, new byte[0][])
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REPORT_WINDOW_CLOSED"));
    }

    @Test
    @DisplayName("RN-34: reserva cancelada pelo ADMIN -> 422 REPORT_RESERVATION_NOT_CONFIRMED")
    void cancelledReservationCannotBeReported() throws Exception {
        UUID reservationId = createConfirmedReservation(unitToken, TOMORROW, "14:00", "15:00");
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"justification\": \"Area interditada pela vistoria\" }"))
            .andExpect(status().isOk());

        createReport(unitToken, reservationId, new byte[0][])
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REPORT_RESERVATION_NOT_CONFIRMED"));
    }

    @Test
    @DisplayName("RN-01/vibe-security: reserva de outra unidade -> 403 FORBIDDEN_RESOURCE")
    void reportOnAnotherUnitReservationIsForbidden() throws Exception {
        UUID reservationId = createConfirmedReservation(unitToken, TOMORROW, "14:00", "15:00");

        createReport(otherUnitToken, reservationId, new byte[0][])
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    @DisplayName("RN-35: descricao com menos de 10 caracteres -> 422 VALIDATION_ERROR")
    void shortDescriptionIsRejected() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");

        String dataJson = """
            { "category": "DAMAGE", "description": "curta", "residentId": "%s" }
            """.formatted(residentId);
        mockMvc.perform(multipart("/api/v1/me/reservations/" + reservationId + "/reports")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RN-01/vibe-security: residentId de outra unidade -> 422 VALIDATION_ERROR")
    void residentFromAnotherUnitIsRejected() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");

        String dataJson = """
            { "category": "DAMAGE", "description": "Churrasqueira suja apos uso", "residentId": "%s" }
            """.formatted(otherResidentId);
        mockMvc.perform(multipart("/api/v1/me/reservations/" + reservationId + "/reports")
                .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RN-17: foto com extensao jpg mas conteudo falso -> 422 INVALID_FILE")
    void fakeImageContentIsRejected() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");

        mockMvc.perform(reportMultipart(reservationId, unitToken, residentId, "Churrasqueira com vazamento de gas",
                new byte[][] {TestImages.fakePdfWithJpgExtension()}))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("RN-35: mais de 5 fotos (6) -> 422 INVALID_FILE")
    void moreThanFivePhotosIsRejected() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        byte[][] sixPhotos = new byte[6][];
        for (int i = 0; i < 6; i++) {
            sixPhotos[i] = TestImages.jpeg();
        }

        mockMvc.perform(reportMultipart(reservationId, unitToken, residentId, "Churrasqueira com vazamento de gas",
                sixPhotos))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_FILE"));
    }

    @Test
    @DisplayName("D-55: morador nao ve comentario interno em GET /me/reports nem GET /reports/{id}")
    void residentDoesNotSeeInternalComments() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reportId = createReportAndGetId(unitToken, reservationId);

        mockMvc.perform(post("/api/v1/reports/" + reportId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"text\": \"Comentario interno da equipe\", \"visibleToResident\": false }"))
            .andExpect(status().isCreated());
        mockMvc.perform(post("/api/v1/reports/" + reportId + "/comments")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"text\": \"Ja estamos vendo isso\", \"visibleToResident\": true }"))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/me/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].comments.length()").value(1))
            .andExpect(jsonPath("$[0].comments[0].visibleToResident").value(true));

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments.length()").value(1));

        mockMvc.perform(get("/api/v1/reports/" + reportId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.comments.length()").value(2));
    }

    @Test
    @DisplayName("RN-36: OPEN pula direto para IN_MAINTENANCE (avanco permitido)")
    void statusCanSkipStagesForward() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reportId = createReportAndGetId(unitToken, reservationId);

        changeStatus(reportId, "IN_MAINTENANCE", null, null)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("IN_MAINTENANCE"));
    }

    @Test
    @DisplayName("RN-36: nao volta (IN_MAINTENANCE -> IN_REVIEW) -> 409 INVALID_STATUS_TRANSITION")
    void statusCannotGoBackward() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reportId = createReportAndGetId(unitToken, reservationId);
        changeStatus(reportId, "IN_MAINTENANCE", null, null).andExpect(status().isOk());

        changeStatus(reportId, "IN_REVIEW", null, null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RN-36: status final (RESOLVED) nao aceita nova transicao -> 409 INVALID_STATUS_TRANSITION")
    void finalStatusRejectsFurtherTransitions() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reportId = createReportAndGetId(unitToken, reservationId);
        changeStatus(reportId, "RESOLVED", null, null).andExpect(status().isOk());

        changeStatus(reportId, "DISMISSED", "Justificativa valida com 10+", null)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RN-36: DISMISSED sem justificativa -> 422 JUSTIFICATION_REQUIRED")
    void dismissWithoutJustificationIsRejected() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reportId = createReportAndGetId(unitToken, reservationId);

        changeStatus(reportId, "DISMISSED", null, null)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("JUSTIFICATION_REQUIRED"));
    }

    @Test
    @DisplayName("RN-37: maintenanceCost fora de RESOLVED -> 422 VALIDATION_ERROR")
    void maintenanceCostOutsideResolvedIsRejected() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reportId = createReportAndGetId(unitToken, reservationId);

        changeStatus(reportId, "IN_REVIEW", null, "50.00")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RN-37: maintenanceCost junto com RESOLVED e gravado")
    void maintenanceCostWithResolvedIsAccepted() throws Exception {
        UUID reservationId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reportId = createReportAndGetId(unitToken, reservationId);

        changeStatus(reportId, "RESOLVED", null, "150.00")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.maintenanceCost").value(150.00))
            .andExpect(jsonPath("$.resolvedAt").exists());
    }

    @Test
    @DisplayName("D-50: /reports/summary conta so reports em status nao final")
    void summaryCountsOnlyNonFinalReports() throws Exception {
        UUID reservationA = insertConfirmedReservation(TODAY, "08:00", "09:00");
        UUID reservationB = insertConfirmedReservation(TODAY, "09:00", "10:00");
        UUID openReportId = createReportAndGetId(unitToken, reservationA);
        UUID resolvedReportId = createReportAndGetId(unitToken, reservationB);
        changeStatus(resolvedReportId, "RESOLVED", null, null).andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/reports/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(1));
        assertThat(openReportId).isNotNull();
    }

    @Test
    @DisplayName("RN-34: canReport true dentro da janela, false antes dela e apos o fim dela")
    void canReportReflectsTheWindow() throws Exception {
        // Ainda nao chegou o dia da reserva (amanha): janela fechada.
        UUID futureReservationId = createConfirmedReservation(unitToken, TOMORROW, "14:00", "15:00");
        // Reserva de hoje: dentro da janela.
        UUID withinWindowId = insertConfirmedReservation(TODAY, "08:00", "09:00");
        // Reserva de 10 dias atras (janela padrao de 7 dias): fora da janela.
        UUID outsideWindowId = insertConfirmedReservation(TODAY.minusDays(10), "08:00", "09:00");

        String upcomingResponse = mockMvc.perform(get("/api/v1/me/reservations").param("scope", "upcoming")
                .param("size", "50")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(canReportOf(upcomingResponse, futureReservationId)).isFalse();

        String pastResponse = mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past")
                .param("size", "50")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(canReportOf(pastResponse, withinWindowId)).isTrue();
        assertThat(canReportOf(pastResponse, outsideWindowId)).isFalse();
    }

    private boolean canReportOf(String pageResponse, UUID reservationId) throws Exception {
        for (JsonNode node : objectMapper.readTree(pageResponse).get("content")) {
            if (reservationId.toString().equals(node.get("id").asText())) {
                return node.get("canReport").asBoolean();
            }
        }
        throw new AssertionError("Reserva " + reservationId + " nao encontrada na resposta.");
    }

    @Test
    @DisplayName("RN-01/D-16: UNIT recebe 403 em GET /reports")
    void unitCannotAccessAdminReportsList() throws Exception {
        mockMvc.perform(get("/api/v1/reports")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    // --- helpers ---

    private org.springframework.test.web.servlet.ResultActions changeStatus(UUID reportId, String status,
        String justification, String maintenanceCost) throws Exception {
        StringBuilder body = new StringBuilder("{ \"status\": \"").append(status).append("\"");
        if (justification != null) {
            body.append(", \"justification\": \"").append(justification).append("\"");
        }
        if (maintenanceCost != null) {
            body.append(", \"maintenanceCost\": ").append(maintenanceCost);
        }
        body.append(" }");
        return mockMvc.perform(patch("/api/v1/reports/" + reportId + "/status")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
            .content(body.toString()));
    }

    private UUID createReportAndGetId(String token, UUID reservationId) throws Exception {
        String response = createReport(token, reservationId, new byte[0][])
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private org.springframework.test.web.servlet.ResultActions createReport(String token, UUID reservationId,
        byte[][] photos) throws Exception {
        return mockMvc.perform(reportMultipart(reservationId, token, residentId,
            "Churrasqueira com vazamento de gas", photos));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder reportMultipart(
        UUID reservationId, String token, UUID residentIdForReport, String description, byte[][] photos) {
        String dataJson = """
            { "category": "DAMAGE", "description": "%s", "residentId": "%s" }
            """.formatted(description, residentIdForReport);
        var request = multipart("/api/v1/me/reservations/" + reservationId + "/reports")
            .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()));
        for (byte[] photo : photos) {
            request.file(new MockMultipartFile("photos", "foto.jpg", "image/jpeg", photo));
        }
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private UUID createConfirmedReservation(String token, LocalDate date, String startTime, String endTime)
        throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": 2 }
            """.formatted(freeAreaId, date, startTime, endTime, residentId);
        String response = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(payload))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservation.status").value("CONFIRMED"))
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("reservation").get("id").asText());
    }

    private UUID createArea() throws Exception {
        String payload = """
            {
              "name": "Area %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 20,
              "requiresPayment": false,
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
            """.formatted(UUID.randomUUID());

        var request = multipart("/api/v1/areas")
            .file(new MockMultipartFile("data", "data", "application/json", payload.getBytes()))
            .file(new MockMultipartFile("photos", "foto.png", "image/png", TestImages.png()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        String response = mockMvc.perform(request)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    // Insere a reserva direto no banco (RN-34 sem depender de avancar o relogio nem revalidar
    // RN-18..24: so os campos que o report precisa) para os testes que nao testam a janela em si.
    private UUID insertConfirmedReservation(LocalDate date, String startTime, String endTime) throws Exception {
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

    private void advanceClockTo(LocalDate date, String time) {
        ZonedDateTime target = ZonedDateTime.of(date, LocalTime.parse(time), ZoneId.of("America/Sao_Paulo"));
        Duration toAdvance = Duration.between(clock.instant(), target.toInstant());
        ((MutableClock) clock).advance(toAdvance);
    }

    private String createAdmin() throws Exception {
        adminEmail = "admin.reports." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminEmail, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    private String createSyndic() throws Exception {
        syndicEmail = "sindico.reports." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'SYNDIC', ?, 'Sindico', ?)",
            condominiumId, syndicEmail, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, syndicEmail, PASSWORD);
    }

    private String createUnitAccount(UUID unitId, String username) throws Exception {
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', ?, ?, ?)",
            condominiumId, username, unitId, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, username, PASSWORD);
    }

    // RN-06: um Bearer emitido antes de advanceClockTo expira (TTL de 15 min) assim que o
    // relogio avanca mais de um dia; refaz o login com o mesmo usuario para continuar chamando
    // a API depois do avanco (mesmo padrao de PaymentControllerTest).
    private String loginUnit() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, unitUsername, PASSWORD);
    }
}
