package br.com.reservas.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
 * F7-1 (RF-SIN-01, RN-31, RN-01): `GET /dashboard/home`. "Agora" (base) é
 * terça 2026-11-10 10:00 em America/Sao_Paulo ({@link ReservationTestConfig}).
 * O "hoje" da página é obtido avançando o relógio de teste até o dia
 * desejado (mesma técnica de {@code ReportControllerTest}), já que reservas
 * não podem ser criadas no mesmo dia (RN-20) nem além do próprio dia da
 * reserva.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class DashboardControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";
    // Primeiro dia reservável a partir do "agora" fixo (RN-20: dia seguinte, dentro da janela default).
    private static final LocalDate REF_DAY = LocalDate.of(2026, 11, 11);

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
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Dashboard");
        adminToken = createAdmin();
        syndicToken = createSyndic();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "a-1");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitUsername = "a-1";
        unitToken = createUnitAccount(unitId, unitUsername);
        areaId = createArea();
        // RN-22 (limite de 3 reservas futuras ativas por unidade) nao e o foco desta suite:
        // alguns testes criam mais de 3 reservas na mesma unidade para exercitar o intervalo de datas.
        jdbcTemplate.update("update condominium_settings set max_active_bookings_per_unit = 10 "
            + "where condominium_id = ?", condominiumId);
    }

    @Test
    @DisplayName("RF-SIN-01: today traz so o dia atual, next7Days de amanha ate hoje+7 (bloqueios inclusos, "
        + "canceladas e fora do intervalo ficam de fora)")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void todayAndNext7DaysComposeActiveReservationsAndBlocks() throws Exception {
        // Antes de avancar o relogio para REF_DAY: um bloqueio "ontem" (visto de REF_DAY) nao pode aparecer.
        UUID pastBlockId = createBlock(adminToken, REF_DAY.minusDays(1), "09:00", "10:00");
        UUID todayResId = createReservation(unitToken, REF_DAY, "09:00", "10:00");
        UUID tomorrowResId = createReservation(unitToken, REF_DAY.plusDays(1), "09:00", "10:00");
        UUID boundaryResId = createReservation(unitToken, REF_DAY.plusDays(7), "09:00", "10:00");
        UUID outsideResId = createReservation(unitToken, REF_DAY.plusDays(8), "09:00", "10:00");
        UUID cancelledResId = createReservation(unitToken, REF_DAY.plusDays(2), "09:00", "10:00");
        cancelReservationAsAdmin(cancelledResId);
        UUID blockId = createBlock(adminToken, REF_DAY.plusDays(3), "09:00", "10:00");

        advanceClockTo(REF_DAY, "00:05");
        String freshSyndicToken = loginSyndic();

        String response = mockMvc.perform(get("/api/v1/dashboard/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshSyndicToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(idsOf(body.get("today"))).containsExactly(todayResId.toString());
        assertThat(idsOf(body.get("next7Days"))).containsExactly(tomorrowResId.toString(), blockId.toString(),
            boundaryResId.toString());
        assertThat(idsOf(body.get("today"))).doesNotContain(pastBlockId.toString());
        assertThat(idsOf(body.get("next7Days"))).doesNotContain(pastBlockId.toString(), outsideResId.toString(),
            cancelledResId.toString());
    }

    @Test
    @DisplayName("RN-31: pendente com inicio ja passado e expirada antes de compor today/next7Days")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void expiresOverduePendingBeforeListing() throws Exception {
        UUID paidAreaId = createArea(true, "150.00", "5562999998888");
        UUID pendingId = createReservationOnArea(unitToken, paidAreaId, REF_DAY, "09:00", "10:00");

        advanceClockTo(REF_DAY, "09:01");
        String freshSyndicToken = loginSyndic();

        String response = mockMvc.perform(get("/api/v1/dashboard/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshSyndicToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(idsOf(body.get("today"))).doesNotContain(pendingId.toString());

        // A expiração já foi gravada (RN-31): /me/reservations mostra CANCELLED.
        String freshUnitToken = loginUnit();
        mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshUnitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("CANCELLED"));
    }

    @Test
    @DisplayName("RF-SIN-01: openReports.count conta status nao final; items traz os 5 mais antigos")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void openReportsCountsNonFinalAndListsFiveOldest() throws Exception {
        UUID confirmedReservationId = createReservation(unitToken, REF_DAY, "09:00", "10:00");
        advanceClockTo(REF_DAY, "12:00");
        String freshUnitToken = loginUnit();
        String freshAdminToken = loginAdmin();

        // RN-34 permite varios reports para a mesma reserva confirmada.
        java.util.List<String> reportIdsInOrder = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            reportIdsInOrder.add(createReport(freshUnitToken, confirmedReservationId));
            advanceClockTo(REF_DAY, "12:0" + (i + 1)); // garante createdAt crescente e distinto
        }
        // O ultimo (mais recente) e resolvido: sai da contagem de "abertos".
        String lastReportId = reportIdsInOrder.get(reportIdsInOrder.size() - 1);
        resolveReport(freshAdminToken, lastReportId);

        String response = mockMvc.perform(get("/api/v1/dashboard/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshAdminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode openReports = objectMapper.readTree(response).get("openReports");
        assertThat(openReports.get("count").asLong()).isEqualTo(6);
        JsonNode items = openReports.get("items");
        assertThat(items).hasSize(5);
        for (int i = 0; i < 5; i++) {
            assertThat(items.get(i).get("id").asText()).isEqualTo(reportIdsInOrder.get(i));
        }
    }

    @Test
    @DisplayName("RF-SIN-01: overdueInspections traz sem-vistoria no topo, exclui recente (<=30d) e area excluida")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void overdueInspectionsRankNoInspectionFirstAndExcludeRecentOrDeleted() throws Exception {
        UUID neverInspectedAreaId = createArea();
        UUID overdueAreaId = createArea();
        UUID recentlyInspectedAreaId = createArea();
        UUID deletedAreaId = createArea();
        deleteArea(deletedAreaId);

        createInspection(overdueAreaId, LocalDate.of(2026, 10, 11)); // 31 dias antes de REF_DAY (11/11)
        createInspection(recentlyInspectedAreaId, LocalDate.of(2026, 10, 12)); // 30 dias, no limite: nao entra
        createInspection(areaId, LocalDate.of(2026, 11, 10)); // area do setUp, vistoria recente

        advanceClockTo(REF_DAY, "00:05");
        String freshSyndicToken = loginSyndic();

        String response = mockMvc.perform(get("/api/v1/dashboard/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshSyndicToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode overdue = objectMapper.readTree(response).get("overdueInspections");
        assertThat(overdue.get(0).get("areaId").asText()).isEqualTo(neverInspectedAreaId.toString());
        assertThat(overdue.get(0).get("lastInspectionAt").isNull()).isTrue();
        assertThat(overdue.get(1).get("areaId").asText()).isEqualTo(overdueAreaId.toString());
        assertThat(overdue.get(1).get("daysSinceInspection").asLong()).isEqualTo(31);

        java.util.List<String> areaIds = new java.util.ArrayList<>();
        overdue.forEach(node -> areaIds.add(node.get("areaId").asText()));
        assertThat(areaIds).doesNotContain(recentlyInspectedAreaId.toString(), this.areaId.toString(),
            deletedAreaId.toString());
    }

    @Test
    // F9-3: propagation NOT_SUPPORTED tira este metodo do @Transactional de classe (que faz a
    // chamada inteira do controller rodar dentro da transacao, ja read-write, do proprio teste) -
    // sem isso o bug de producao (readOnly=true em cima do UPDATE de RN-31) nao aparece aqui.
    @org.springframework.transaction.annotation.Transactional(
        propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    @DisplayName("RF-SIN-01/RN-31: GET /dashboard/home expira pendente vencida sem estourar "
        + "'cannot execute UPDATE in a read-only transaction'")
    void homeExpiresOverduePendingWithoutReadOnlyTransactionError() throws Exception {
        // NOT_SUPPORTED nao faz rollback (D-13/username unico por condominio: um segundo
        // condominio sobrevivendo quebraria o login de outros testes que reusam "a-1") -
        // por isso este teste desfaz tudo que criou (setUp incluso) no finally abaixo.
        try {
            UUID paidAreaId = createArea(true, "150.00", "5562999998888");
            UUID pendingId = createReservationOnArea(unitToken, paidAreaId, REF_DAY, "09:00", "10:00");

            advanceClockTo(REF_DAY, "09:01");
            String freshSyndicToken = loginSyndic();

            String response = mockMvc.perform(get("/api/v1/dashboard/home")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + freshSyndicToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

            JsonNode body = objectMapper.readTree(response);
            assertThat(idsOf(body.get("today"))).doesNotContain(pendingId.toString());
        } finally {
            cleanupCondominiumData(condominiumId);
        }
    }

    // Usado apenas pelo teste NOT_SUPPORTED acima: apaga, na ordem certa de FKs, tudo que o
    // setUp() e o corpo do teste gravaram fora de uma transacao de teste.
    private void cleanupCondominiumData(UUID targetCondominiumId) {
        jdbcTemplate.update("delete from report_comment where report_id in "
            + "(select id from report where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from report_photo where report_id in "
            + "(select id from report where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from report where condominium_id = ?", targetCondominiumId);
        jdbcTemplate.update("delete from reservation_event where reservation_id in "
            + "(select id from reservation where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from reservation where condominium_id = ?", targetCondominiumId);
        jdbcTemplate.update("delete from area_inspection where area_id in "
            + "(select id from common_area where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from area_photo where area_id in "
            + "(select id from common_area where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from area_opening_hours where area_id in "
            + "(select id from common_area where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from common_area where condominium_id = ?", targetCondominiumId);
        jdbcTemplate.update("delete from resident where unit_id in "
            + "(select id from unit where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from audit_log where condominium_id = ?", targetCondominiumId);
        jdbcTemplate.update("delete from refresh_token where user_id in "
            + "(select id from user_account where condominium_id = ?)", targetCondominiumId);
        jdbcTemplate.update("delete from user_account where condominium_id = ?", targetCondominiumId);
        jdbcTemplate.update("delete from unit where condominium_id = ?", targetCondominiumId);
        jdbcTemplate.update("delete from condominium_settings where condominium_id = ?", targetCondominiumId);
        jdbcTemplate.update("delete from condominium where id = ?", targetCondominiumId);
    }

    @Test
    @DisplayName("RN-01: conta UNIT recebe 403 em GET /dashboard/home")
    void unitAccountIsForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/home")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    private static java.util.List<String> idsOf(JsonNode array) {
        java.util.List<String> ids = new java.util.ArrayList<>();
        array.forEach(node -> ids.add(node.get("id").asText()));
        return ids;
    }

    private void advanceClockTo(LocalDate date, String time) {
        var target = java.time.ZonedDateTime.of(date, java.time.LocalTime.parse(time),
            java.time.ZoneId.of("America/Sao_Paulo")).toInstant();
        Duration toAdvance = Duration.between(clock.instant(), target);
        ((MutableClock) clock).advance(toAdvance);
    }

    private UUID createReservation(String token, LocalDate date, String startTime, String endTime) throws Exception {
        return createReservationOnArea(token, areaId, date, startTime, endTime);
    }

    private UUID createReservationOnArea(String token, UUID targetAreaId, LocalDate date, String startTime,
        String endTime) throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": 2 }
            """.formatted(targetAreaId, date, startTime, endTime, residentId);
        String response = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("reservation").get("id").asText());
    }

    private void cancelReservationAsAdmin(UUID reservationId) throws Exception {
        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"justification\": \"Area em manutencao programada\" }"))
            .andExpect(status().isOk());
    }

    private UUID createBlock(String token, LocalDate date, String startTime, String endTime) throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "reason": "Assembleia geral" }
            """.formatted(areaId, date, startTime, endTime);
        String response = mockMvc.perform(post("/api/v1/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String createReport(String token, UUID reservationId) throws Exception {
        String payload = """
            { "category": "DAMAGE", "description": "Descricao valida do problema", "residentId": "%s" }
            """.formatted(residentId);
        var request = multipart("/api/v1/me/reservations/" + reservationId + "/reports")
            .file(new MockMultipartFile("data", "data", "application/json", payload.getBytes()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        String response = mockMvc.perform(request)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void resolveReport(String token, String reportId) throws Exception {
        mockMvc.perform(patch("/api/v1/reports/" + reportId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("{ \"status\": \"RESOLVED\", \"maintenanceCost\": \"50.00\" }"))
            .andExpect(status().isOk());
    }

    private void createInspection(UUID targetAreaId, LocalDate inspectedAt) throws Exception {
        String payload = """
            { "inspectedAt": "%s", "overallCondition": "GOOD", "notes": "Sem problemas" }
            """.formatted(inspectedAt);
        var request = multipart("/api/v1/areas/" + targetAreaId + "/inspections")
            .file(new MockMultipartFile("data", "data", "application/json", payload.getBytes()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        mockMvc.perform(request).andExpect(status().isCreated());
    }

    private void deleteArea(UUID targetAreaId) throws Exception {
        mockMvc.perform(delete("/api/v1/areas/" + targetAreaId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNoContent());
    }

    private UUID createArea() throws Exception {
        return createArea(false, null, null);
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
              "paymentWhatsapp": %s,
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
            """.formatted(UUID.randomUUID(), requiresPayment, price == null ? "null" : price,
            whatsapp == null ? "null" : "\"" + whatsapp + "\"");

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
        adminEmail = "admin.dashboard." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminEmail, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    private String createSyndic() throws Exception {
        syndicEmail = "sindico.dashboard." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'SYNDIC', ?, 'Sindico', ?)",
            condominiumId, syndicEmail, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, syndicEmail, PASSWORD);
    }

    private String createUnitAccount(UUID id, String username) throws Exception {
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', ?, ?, ?)",
            condominiumId, username, id, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, username, PASSWORD);
    }

    // RN-06: um Bearer emitido antes de advanceClockTo expira (TTL 15 min, mesmo Clock de teste);
    // refaz o login com o mesmo usuario para continuar chamando a API depois do avanco.
    private String loginAdmin() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    private String loginSyndic() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, syndicEmail, PASSWORD);
    }

    private String loginUnit() throws Exception {
        return ApiLogin.token(mockMvc, objectMapper, unitUsername, PASSWORD);
    }
}
