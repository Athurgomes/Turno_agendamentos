package br.com.reservas.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * FD-2 parcial (docs/08 §2, D-59): `DemoSeedRunner` cria o cenário de
 * demonstração (exceto o histórico de ~6 meses, Etapa 3) com um {@link
 * ReservationTestConfig#reservationTestClock() Clock fixo} — "agora" =
 * terça 2026-11-10 10:00 em America/Sao_Paulo, o mesmo exemplo do roteiro
 * (`APP_DEMO_NOW`).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@ActiveProfiles("demo")
@TestPropertySource(properties = {
    "app.seed.demo.enabled=true",
    "app.seed.demo.password=SenhaDemo123",
    "app.seed.demo.temp-password=K7M4XP"
})
@Transactional
class DemoSeedRunnerTest extends AbstractIntegrationTest {

    private static final String DEMO_PASSWORD = "SenhaDemo123";
    private static final String DEMO_TEMP_PASSWORD = "K7M4XP";
    private static final String ADMIN_PASSWORD = "SenhaForteAdmin1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DemoSeedRunner runner;

    private String adminEmail;

    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void setUp() {
        Logger runnerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(DemoSeedRunner.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        runnerLogger.addAppender(logAppender);

        UUID condominiumId = jdbcTemplate.queryForObject(
            "insert into condominium (id, name) values (gen_random_uuid(), 'Condominio Seed Demo') returning id",
            UUID.class);
        jdbcTemplate.update("insert into condominium_settings (condominium_id) values (?)", condominiumId);
        adminEmail = "admin.seed." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminEmail, passwordEncoder.encode(ADMIN_PASSWORD));
    }

    @Test
    @DisplayName("docs/08 §2: seed demo cria as contas do cenario e todas logam")
    void createsAndLogsInAllAccounts() throws Exception {
        runner.run(null);

        assertLogin("sindico@exemplo.test", DEMO_PASSWORD);
        assertLogin("a-101", DEMO_PASSWORD);
        assertLogin("b-201", DEMO_PASSWORD);
        assertLogin("b-202", DEMO_PASSWORD);
        assertLogin("a-103", DEMO_PASSWORD);
        assertLogin("b-203", DEMO_PASSWORD);
        String a102Token = ApiLogin.token(mockMvc, objectMapper, "a-102", DEMO_TEMP_PASSWORD);
        mockMvc.perform(get("/api/v1/me/unit").header(HttpHeaders.AUTHORIZATION, "Bearer " + a102Token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("docs/08 §2/RN-03: a-102 loga com APP_DEMO_TEMP_PASSWORD e tempPassword=true")
    void a102HasTemporaryPassword() throws Exception {
        runner.run(null);

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("login", "a-102", "password", DEMO_TEMP_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(response).path("user").path("tempPassword").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("RN-22: b-201 tem exatamente 3 reservas futuras ativas e a 4a recebe UNIT_BOOKING_LIMIT_REACHED")
    void b201IsAtBookingLimit() throws Exception {
        runner.run(null);

        String token = ApiLogin.token(mockMvc, objectMapper, "b-201", DEMO_PASSWORD);
        String meResponse = mockMvc.perform(get("/api/v1/me/reservations").param("scope", "upcoming")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(meResponse).path("content")).hasSize(3);

        Long activeCount = jdbcTemplate.queryForObject(
            "select count(*) from reservation r join unit u on u.id = r.unit_id "
                + "where u.identifier = 'b-201' and r.status in ('PENDING_PAYMENT', 'CONFIRMED') "
                + "and r.start_at > now()",
            Long.class);
        assertThat(activeCount).isEqualTo(3);

        UUID areaId = jdbcTemplate.queryForObject(
            "select id from common_area where name = 'Espaço gourmet'", UUID.class);
        UUID residentId = jdbcTemplate.queryForObject(
            "select r.id from resident r join unit u on u.id = r.unit_id where u.identifier = 'b-201' and r.is_primary",
            UUID.class);
        String body = objectMapper.writeValueAsString(Map.of(
            "areaId", areaId, "date", "2026-11-25", "startTime", "09:00", "endTime", "10:00",
            "residentId", residentId, "guests", 2));
        mockMvc.perform(post("/api/v1/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("UNIT_BOOKING_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("RN-25/RN-26: b-202 tem 1 reserva PENDING_PAYMENT daqui a 5 dias com whatsappPaymentUrl")
    void b202HasPendingPayment() throws Exception {
        runner.run(null);

        String token = ApiLogin.token(mockMvc, objectMapper, "b-202", DEMO_PASSWORD);
        String response = mockMvc.perform(get("/api/v1/me/reservations").param("scope", "upcoming")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var content = objectMapper.readTree(response).path("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).path("status").asText()).isEqualTo("PENDING_PAYMENT");
        assertThat(content.get(0).path("whatsappPaymentUrl").asText()).startsWith("https://wa.me/");
    }

    @Test
    @DisplayName("RN-34: a-101 pode reportar a reserva de ontem, mas nao a de 10 dias atras")
    void a101ReportWindow() throws Exception {
        runner.run(null);

        String token = ApiLogin.token(mockMvc, objectMapper, "a-101", DEMO_PASSWORD);
        String response = mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past").param("size", "10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var content = objectMapper.readTree(response).path("content");
        assertThat(content).hasSize(2);
        // scope=past vem em ordem decrescente: [0] = ontem, [1] = ha 10 dias.
        assertThat(content.get(0).path("canReport").asBoolean()).isTrue();
        assertThat(content.get(1).path("canReport").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("docs/08 §2: bloqueio 'Assembleia geral' no Salao daqui a 7 dias")
    void hasAssemblyBlock() {
        Long count = countBlockedByReason("Assembleia geral");
        runner.run(null);
        count = countBlockedByReason("Assembleia geral");
        assertThat(count).isEqualTo(1);
    }

    private Long countBlockedByReason(String reason) {
        return jdbcTemplate.queryForObject(
            "select count(*) from reservation where kind = 'BLOCK' and notes = ?", Long.class, reason);
    }

    @Test
    @DisplayName("docs/08 §2: Piscina fica em manutencao e a Quadra tem reserva futura ativa")
    void poolInMaintenanceAndCourtHasFutureReservation() throws Exception {
        runner.run(null);

        String poolStatus = jdbcTemplate.queryForObject(
            "select status from common_area where name = 'Piscina'", String.class);
        assertThat(poolStatus).isEqualTo("MAINTENANCE");

        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);
        UUID courtId = jdbcTemplate.queryForObject(
            "select id from common_area where name = 'Quadra poliesportiva'", UUID.class);
        mockMvc.perform(patch("/api/v1/areas/" + courtId + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "MAINTENANCE"))))
            .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("docs/08 §2: report OPEN historico conta em /reports/summary")
    void openReportCountsInSummary() throws Exception {
        runner.run(null);

        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);
        mockMvc.perform(get("/api/v1/reports/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.open").value(1));
    }

    @Test
    @DisplayName("FD-2: rodar o seed demo de novo nao duplica nada (idempotente)")
    void doesNotDuplicateOnSecondRun() {
        runner.run(null);
        long unitsAfterFirst = countUnits();
        long reservationsAfterFirst = countReservations();

        runner.run(null);

        assertThat(countUnits()).isEqualTo(unitsAfterFirst);
        assertThat(countReservations()).isEqualTo(reservationsAfterFirst);
    }

    @Test
    @DisplayName("D-59: o mesmo Clock produz sempre o mesmo CPF do morador principal de a-101")
    void deterministicResidentData() {
        runner.run(null);

        String cpf = jdbcTemplate.queryForObject(
            "select r.cpf from resident r join unit u on u.id = r.unit_id "
                + "where u.identifier = 'a-101' and r.is_primary", String.class);
        assertThat(cpf).isEqualTo("19827364537");
    }

    private long countUnits() {
        Long count = jdbcTemplate.queryForObject("select count(*) from unit", Long.class);
        return count == null ? 0 : count;
    }

    private long countReservations() {
        Long count = jdbcTemplate.queryForObject("select count(*) from reservation", Long.class);
        return count == null ? 0 : count;
    }

    private void assertLogin(String login, String password) throws Exception {
        String token = ApiLogin.token(mockMvc, objectMapper, login, password);
        assertThat(token).isNotBlank();
    }

    @AfterEach
    void tearDown() {
        Logger runnerLogger = (Logger) org.slf4j.LoggerFactory.getLogger(DemoSeedRunner.class);
        runnerLogger.detachAppender(logAppender);
    }

    @Test
    @DisplayName("FD-2: log final do seed demo cita a contagem real de unidades criadas (6, não 7)")
    void logsActualUnitCount() {
        runner.run(null);

        assertThat(logAppender.list)
            .extracting(ILoggingEvent::getFormattedMessage)
            .anyMatch(message -> message.contains("6 unidades") && message.contains("6 áreas"));
    }

    @Test
    @DisplayName("FD-2: APP_DEMO_ADMIN_WHATSAPP vazio gera warn claro apontando docs/08 §5")
    void warnsWhenAdminWhatsappIsBlank() {
        runner.run(null);

        assertThat(logAppender.list)
            .anyMatch(event -> event.getLevel() == ch.qos.logback.classic.Level.WARN
                && event.getFormattedMessage().contains("APP_DEMO_ADMIN_WHATSAPP vazio")
                && event.getFormattedMessage().contains("docs/08 §5"));
    }

    @Test
    @DisplayName("FD-2: seed demo e deterministico e idempotente no total de reservas historicas")
    void historyIsDeterministicAndIdempotent() {
        runner.run(null);
        long total = countReservations();
        assertThat(total).isGreaterThan(50); // ~6 meses de historico, alem das situacoes pre-montadas.

        runner.run(null); // idempotente: segunda chamada nao roda de novo (ja existe unidade).
        assertThat(countReservations()).isEqualTo(total);
    }

    @Test
    @DisplayName("FD-2/D-43: seed demo preenche defaultPaymentWhatsapp e o GET/PUT de /admin/settings funciona (roteiro passo 11)")
    void seedFillsDefaultPaymentWhatsappForSettingsScreen() throws Exception {
        runner.run(null);

        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);
        String getResponse = mockMvc.perform(get("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultPaymentWhatsapp").isNotEmpty())
            .andReturn().getResponse().getContentAsString();

        mockMvc.perform(put("/api/v1/admin/settings")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(getResponse))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.defaultPaymentWhatsapp").isNotEmpty());
    }

    @Test
    @DisplayName("RF-DAS-02: GET /dashboard/summary do mes bate com contagem direta no banco")
    void dashboardSummaryMatchesDirectSql() throws Exception {
        runner.run(null);
        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);

        String response = mockMvc.perform(get("/api/v1/dashboard/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var json = objectMapper.readTree(response);
        String from = json.path("from").asText();
        String to = json.path("to").asText();

        Long expectedTotal = jdbcTemplate.queryForObject(
            "select count(*) from reservation where kind = 'BOOKING' "
                + "and ((start_at at time zone 'America/Sao_Paulo')::date) between ?::date and ?::date",
            Long.class, from, to);
        Long expectedConfirmed = jdbcTemplate.queryForObject(
            "select count(*) from reservation where kind = 'BOOKING' and status = 'CONFIRMED' "
                + "and ((start_at at time zone 'America/Sao_Paulo')::date) between ?::date and ?::date",
            Long.class, from, to);
        Long expectedCancelledByResident = jdbcTemplate.queryForObject(
            "select count(*) from reservation where kind = 'BOOKING' and status = 'CANCELLED' "
                + "and cancelled_by = 'RESIDENT' "
                + "and ((start_at at time zone 'America/Sao_Paulo')::date) between ?::date and ?::date",
            Long.class, from, to);
        java.math.BigDecimal expectedConfirmedAmount = jdbcTemplate.queryForObject(
            "select coalesce(sum(price_snapshot), 0) from reservation where kind = 'BOOKING' "
                + "and status = 'CONFIRMED' and requires_payment_snapshot "
                + "and ((start_at at time zone 'America/Sao_Paulo')::date) between ?::date and ?::date",
            java.math.BigDecimal.class, from, to);

        assertThat(json.path("reservations").path("total").asLong()).isEqualTo(expectedTotal);
        assertThat(json.path("reservations").path("confirmed").asLong()).isEqualTo(expectedConfirmed);
        assertThat(json.path("cancellations").path("byResident").asLong()).isEqualTo(expectedCancelledByResident);
        assertThat(json.path("amounts").path("confirmed").asDouble())
            .isEqualTo(expectedConfirmedAmount.setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
        assertThat(expectedConfirmedAmount).isGreaterThan(java.math.BigDecimal.ZERO);
    }

    @Test
    @DisplayName("RF-DAS-02: GET /dashboard/reservations-by-month bate com contagem direta no banco (12 meses)")
    void reservationsByMonthMatchesDirectSql() throws Exception {
        runner.run(null);
        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);

        String response = mockMvc.perform(get("/api/v1/dashboard/reservations-by-month")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var months = objectMapper.readTree(response);
        assertThat(months).hasSize(12);

        for (var month : months) {
            String key = month.path("month").asText();
            Long expectedTotal = jdbcTemplate.queryForObject(
                "select count(*) from reservation where kind = 'BOOKING' "
                    + "and to_char((start_at at time zone 'America/Sao_Paulo'), 'YYYY-MM') = ?",
                Long.class, key);
            assertThat(month.path("total").asLong()).as("total do mes " + key).isEqualTo(expectedTotal);
        }
    }

    @Test
    @DisplayName("D-62: mapa de calor do historico tem mais demanda em sex/sab/dom que em seg-qui")
    void heatmapHasMoreWeekendDemand() throws Exception {
        runner.run(null);
        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);

        String response = mockMvc.perform(get("/api/v1/dashboard/demand-heatmap")
                .param("from", "2026-05-10").param("to", "2026-11-09")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var cells = objectMapper.readTree(response);

        long weekendCount = 0;
        long weekdayCount = 0;
        for (var cell : cells) {
            int dayOfWeek = cell.path("dayOfWeek").asInt(); // ISO: 1=segunda ... 7=domingo
            long count = cell.path("count").asLong();
            if (dayOfWeek == 5 || dayOfWeek == 6 || dayOfWeek == 7) {
                weekendCount += count;
            } else {
                weekdayCount += count;
            }
        }
        assertThat(weekendCount).isGreaterThan(weekdayCount);
    }

    @Test
    @DisplayName("FD-2/RF-DAS-02: heatmap de 6 meses nao acende uma hora fixa (ex.: 08h) em todos os 7 dias")
    void heatmapHasNoSingleHourArtifactAcrossAllWeekdays() throws Exception {
        runner.run(null);
        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);

        String response = mockMvc.perform(get("/api/v1/dashboard/demand-heatmap")
                .param("from", "2026-05-10").param("to", "2026-11-09")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var cells = objectMapper.readTree(response);

        Map<Integer, java.util.Set<Integer>> daysByHourWithHighCount = new java.util.HashMap<>();
        for (var cell : cells) {
            int hour = cell.path("hour").asInt();
            int dayOfWeek = cell.path("dayOfWeek").asInt();
            long count = cell.path("count").asLong();
            if (count >= 5) {
                daysByHourWithHighCount.computeIfAbsent(hour, key -> new java.util.HashSet<>()).add(dayOfWeek);
            }
        }
        daysByHourWithHighCount.forEach((hour, days) -> assertThat(days)
            .as("hora %d nao deveria acender count>=5 em todos os 7 dias da semana", hour)
            .hasSizeLessThan(7));
    }

    @Test
    @DisplayName("FD-2/RF-DAS-02: GET /dashboard/top-units devolve 10 unidades com contagens nao todas iguais")
    void topUnitsHasTenUnitsNotAllTied() throws Exception {
        runner.run(null);
        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);

        String response = mockMvc.perform(get("/api/v1/dashboard/top-units")
                .param("from", "2026-05-10").param("to", "2026-11-09")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        var units = objectMapper.readTree(response);

        assertThat(units).hasSize(10);
        java.util.Set<Long> counts = new java.util.HashSet<>();
        units.forEach(u -> counts.add(u.path("reservations").asLong()));
        assertThat(counts).as("contagens nao devem ser todas iguais (ranking escalonado)").hasSizeGreaterThan(1);
    }

    @Test
    @DisplayName("RF-SIN-01: GET /dashboard/home traz reserva hoje, proximos 7 dias, report aberto e vistoria atrasada")
    void dashboardHomeHasRichScenario() throws Exception {
        runner.run(null);
        String adminToken = ApiLogin.token(mockMvc, objectMapper, adminEmail, ADMIN_PASSWORD);

        mockMvc.perform(get("/api/v1/dashboard/home").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.today").isNotEmpty())
            .andExpect(jsonPath("$.next7Days").isNotEmpty())
            .andExpect(jsonPath("$.openReports.count", org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
            .andExpect(jsonPath("$.overdueInspections").isNotEmpty());
    }
}
