package br.com.reservas.dashboard.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.TestImages;
import br.com.reservas.reservation.support.ReservationFixtures;
import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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
 * F8-1 (RF-DAS-01, RF-DAS-02, D-61/D-62): `/dashboard/summary`,
 * `/reservations-by-month`, `/areas`, `/demand-heatmap`, `/top-units`. "Agora"
 * fixo = terça 2026-11-10 13:00 UTC = 10:00 em America/Sao_Paulo
 * ({@link ReservationTestConfig}), então o mês corrente (default do período,
 * RF-DAS-01) é novembro/2026. Reservas e reports são inseridos direto via
 * JDBC (mesma técnica de {@code SeedHistoryWriter}, D-59): os serviços
 * públicos de `reservation`/`report` não permitem controlar datas passadas
 * nem futuras arbitrárias, e este módulo é só leitura (D-61).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class DashboardMetricsControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";
    private static final String ZONE = "America/Sao_Paulo";

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
    private String unitToken;
    private UUID adminId;
    private UUID unitId;
    private UUID residentId;
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Dashboard Metrics");
        adminToken = createAdmin();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "a-1");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitToken = createUnitAccount(unitId, "a-1");
        areaId = createArea("08:00", "18:00");
    }

    @Test
    @DisplayName("RF-DAS-01: from > to retorna 400 VALIDATION_ERROR")
    void fromAfterToIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .param("from", "2026-11-10").param("to", "2026-11-01")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RF-DAS-01: intervalo maior que 366 dias retorna 400 VALIDATION_ERROR")
    void rangeLongerThan366DaysIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                .param("from", "2025-01-01").param("to", "2026-01-03")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RF-DAS-01: periodo default (sem from/to) e o mes corrente pelo Clock")
    void defaultPeriodIsCurrentMonth() throws Exception {
        // Dentro de novembro/2026 (mes corrente).
        insertReservation("PENDING_PAYMENT", null, localInstant(2026, 11, 15, 9, 0),
            localInstant(2026, 11, 15, 10, 0), false, null);
        // Fora do periodo (dezembro/2026): nao deve entrar no total.
        insertReservation("CONFIRMED", null, localInstant(2026, 12, 1, 9, 0),
            localInstant(2026, 12, 1, 10, 0), false, null);

        String response = mockMvc.perform(get("/api/v1/dashboard/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode body = objectMapper.readTree(response);
        assertThat(body.get("from").asText()).isEqualTo("2026-11-01");
        assertThat(body.get("to").asText()).isEqualTo("2026-11-30");
        assertThat(body.get("reservations").get("total").asLong()).isEqualTo(1);
        assertThat(body.get("reservations").get("pendingPayment").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-DAS-02: bloqueios ficam fora dos indicadores de reserva (summary e demand-heatmap)")
    void blocksAreExcludedFromReservationIndicators() throws Exception {
        insertReservation("CONFIRMED", null, localInstant(2026, 11, 15, 9, 0), localInstant(2026, 11, 15, 10, 0),
            false, null);
        insertBlock(localInstant(2026, 11, 16, 9, 0), localInstant(2026, 11, 16, 10, 0));

        String summary = mockMvc.perform(get("/api/v1/dashboard/summary")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(summary).get("reservations").get("total").asLong()).isEqualTo(1);

        String heatmap = mockMvc.perform(get("/api/v1/dashboard/demand-heatmap")
                .param("from", "2026-11-01").param("to", "2026-11-30")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode cells = objectMapper.readTree(heatmap);
        assertThat(cells).hasSize(1); // só a reserva; o bloqueio (kind=BLOCK) não entra.
    }

    @Test
    @DisplayName("RNF-10: reserva com inicio 22:00 local do ultimo dia do periodo entra pela data local, "
        + "mesmo cruzando a borda em UTC")
    void reservationAtLocalMidnightBoundaryEntersByLocalStartDate() throws Exception {
        // 2026-11-30 22:00 em America/Sao_Paulo (UTC-3) = 2026-12-01 01:00 UTC.
        Instant start = localInstant(2026, 11, 30, 22, 0);
        Instant end = localInstant(2026, 11, 30, 23, 0);
        insertReservation("CONFIRMED", null, start, end, false, null);

        String summary = mockMvc.perform(get("/api/v1/dashboard/summary")
                .param("from", "2026-11-01").param("to", "2026-11-30")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(summary).get("reservations").get("total").asLong()).isEqualTo(1);

        String byMonth = mockMvc.perform(get("/api/v1/dashboard/reservations-by-month")
                .param("to", "2026-11-30")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode months = objectMapper.readTree(byMonth);
        JsonNode november = lastMonth(months);
        assertThat(november.get("month").asText()).isEqualTo("2026-11");
        assertThat(november.get("total").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-DAS-02: ocupacao = horas reservadas / horas de funcionamento, valores monetarios com 2 casas")
    void occupancyRateAndAmountsWithTwoDecimals() throws Exception {
        // Area do setUp abre 08:00-18:00 todo dia = 10h/dia; periodo de 1 dia = 10h disponiveis.
        insertReservation("CONFIRMED", null, localInstant(2026, 11, 15, 9, 0), localInstant(2026, 11, 15, 11, 0),
            true, new BigDecimal("150.00"));

        String areas = mockMvc.perform(get("/api/v1/dashboard/areas")
                .param("from", "2026-11-15").param("to", "2026-11-15")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode area = objectMapper.readTree(areas).get(0);
        assertThat(area.get("reservedHours").decimalValue()).isEqualByComparingTo("2.0000");
        assertThat(area.get("availableHours").decimalValue()).isEqualByComparingTo("10.0000");
        assertThat(area.get("occupancyRate").decimalValue()).isEqualByComparingTo("0.2000");

        String summary = mockMvc.perform(get("/api/v1/dashboard/summary")
                .param("from", "2026-11-15").param("to", "2026-11-15")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode amounts = objectMapper.readTree(summary).get("amounts");
        assertThat(amounts.get("confirmed").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(amounts.get("pending").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("RF-DAS-02: mapa de calor conta reservas canceladas (demanda, nao ocupacao)")
    void heatmapCountsCancelledReservations() throws Exception {
        insertReservation("CANCELLED", "RESIDENT", localInstant(2026, 11, 17, 14, 0),
            localInstant(2026, 11, 17, 15, 0), false, null);

        String heatmap = mockMvc.perform(get("/api/v1/dashboard/demand-heatmap")
                .param("from", "2026-11-01").param("to", "2026-11-30")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode cells = objectMapper.readTree(heatmap);
        assertThat(cells).hasSize(1);
        // 2026-11-17 e uma terca-feira (isodow = 2); 14h local.
        assertThat(cells.get(0).get("dayOfWeek").asInt()).isEqualTo(2);
        assertThat(cells.get(0).get("hour").asInt()).isEqualTo(14);
        assertThat(cells.get(0).get("count").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("RF-DAS-02: top-units limita a 10 e ordena por reservas desc, depois por identificador")
    void topUnitsLimitsToTenOrderedByReservationsDesc() throws Exception {
        int slot = 0; // cada reserva ocupa um slot unico (dia, hora) na mesma area, para nao violar RN-24.
        for (int i = 0; i < 11; i++) {
            UUID otherUnitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "b-" + i);
            UUID otherResidentId = ReservationFixtures.insertResident(jdbcTemplate, otherUnitId, "Morador " + i);
            int reservationCount = i + 1; // b-10 tem 11 reservas (mais que qualquer outra), b-0 tem 1.
            for (int r = 0; r < reservationCount; r++) {
                int day = 2 + (slot / 10);
                int hour = 8 + (slot % 10);
                slot++;
                insertReservationForUnit(otherUnitId, otherResidentId, "CONFIRMED", null,
                    localInstant(2026, 11, day, hour, 0), localInstant(2026, 11, day, hour + 1, 0), false, null);
            }
        }

        String response = mockMvc.perform(get("/api/v1/dashboard/top-units")
                .param("from", "2026-11-01").param("to", "2026-11-30")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode top = objectMapper.readTree(response);
        assertThat(top).hasSize(10);
        assertThat(top.get(0).get("unitIdentifier").asText()).isEqualTo("b-10");
        assertThat(top.get(0).get("reservations").asLong()).isEqualTo(11);
    }

    @Test
    @DisplayName("RF-DAS-02: serie de reservations-by-month tem sempre 12 meses, com zeros nos meses sem reserva")
    void reservationsByMonthAlwaysHasTwelveMonthsWithZeros() throws Exception {
        // Mes corrente (novembro/2026) com 1 reserva; os outros 11 meses ficam com zero.
        insertReservation("CONFIRMED", null, localInstant(2026, 11, 5, 9, 0), localInstant(2026, 11, 5, 10, 0),
            false, null);

        String response = mockMvc.perform(get("/api/v1/dashboard/reservations-by-month")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode months = objectMapper.readTree(response);
        assertThat(months).hasSize(12);
        assertThat(months.get(0).get("month").asText()).isEqualTo("2025-12");
        assertThat(months.get(11).get("month").asText()).isEqualTo("2026-11");
        assertThat(months.get(11).get("total").asLong()).isEqualTo(1);
        for (int i = 0; i < 11; i++) {
            assertThat(months.get(i).get("total").asLong()).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("RN-01: conta UNIT recebe 403 em todos os endpoints agregados do dashboard")
    void unitAccountIsForbiddenOnAllAggregateEndpoints() throws Exception {
        for (String path : List.of("/api/v1/dashboard/summary", "/api/v1/dashboard/reservations-by-month",
            "/api/v1/dashboard/areas", "/api/v1/dashboard/demand-heatmap", "/api/v1/dashboard/top-units")) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
                .andExpect(status().isForbidden());
        }
    }

    // --- helpers ---

    private static Instant localInstant(int year, int month, int day, int hour, int minute) {
        return java.time.ZonedDateTime.of(year, month, day, hour, minute, 0, 0, java.time.ZoneId.of(ZONE))
            .toInstant();
    }

    private static JsonNode lastMonth(JsonNode months) {
        return months.get(months.size() - 1);
    }

    private UUID insertReservation(String status, String cancelledBy, Instant startAt, Instant endAt,
        boolean requiresPayment, BigDecimal price) {
        return insertReservationForUnit(unitId, residentId, status, cancelledBy, startAt, endAt, requiresPayment,
            price);
    }

    private UUID insertReservationForUnit(UUID targetUnitId, UUID targetResidentId, String status,
        String cancelledBy, Instant startAt, Instant endAt, boolean requiresPayment, BigDecimal price) {
        UUID id = UUID.randomUUID();
        long sequence = jdbcTemplate.queryForObject("select nextval('reservation_code_seq')", Long.class);
        String code = "RES-%d-%06d".formatted(startAt.atZone(ZoneOffset.UTC).getYear(), sequence);
        jdbcTemplate.update("""
            insert into reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id,
                resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status,
                cancelled_by, requires_payment_snapshot, price_snapshot, created_by)
            values (?, ?, ?, ?, 'BOOKING', ?, ?, 'Morador Teste', '5562999990000', ?, ?, 2, ?, ?, ?, ?, ?)
            """, id, code, condominiumId, areaId, targetUnitId, targetResidentId, Timestamp.from(startAt),
            Timestamp.from(endAt), status, cancelledBy, requiresPayment, price, adminId);
        return id;
    }

    private UUID insertBlock(Instant startAt, Instant endAt) {
        UUID id = UUID.randomUUID();
        long sequence = jdbcTemplate.queryForObject("select nextval('reservation_code_seq')", Long.class);
        String code = "RES-%d-%06d".formatted(startAt.atZone(ZoneOffset.UTC).getYear(), sequence);
        jdbcTemplate.update("""
            insert into reservation (id, code, condominium_id, area_id, kind, start_at, end_at, status, created_by)
            values (?, ?, ?, ?, 'BLOCK', ?, ?, 'CONFIRMED', ?)
            """, id, code, condominiumId, areaId, Timestamp.from(startAt), Timestamp.from(endAt), adminId);
        return id;
    }

    private UUID createArea(String openTime, String closeTime) throws Exception {
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
                { "dayOfWeek": 1, "openTime": "%s", "closeTime": "%s" },
                { "dayOfWeek": 2, "openTime": "%s", "closeTime": "%s" },
                { "dayOfWeek": 3, "openTime": "%s", "closeTime": "%s" },
                { "dayOfWeek": 4, "openTime": "%s", "closeTime": "%s" },
                { "dayOfWeek": 5, "openTime": "%s", "closeTime": "%s" },
                { "dayOfWeek": 6, "openTime": "%s", "closeTime": "%s" },
                { "dayOfWeek": 7, "openTime": "%s", "closeTime": "%s" }
              ]
            }
            """.formatted(UUID.randomUUID(), openTime, closeTime, openTime, closeTime, openTime, closeTime,
            openTime, closeTime, openTime, closeTime, openTime, closeTime, openTime, closeTime);

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
        String adminEmail = "admin.dashboard.metrics." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, adminEmail, passwordEncoder.encode(PASSWORD));
        adminId = jdbcTemplate.queryForObject("select id from user_account where email = ?", UUID.class, adminEmail);
        return ApiLogin.token(mockMvc, objectMapper, adminEmail, PASSWORD);
    }

    private String createUnitAccount(UUID id, String username) throws Exception {
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', ?, ?, ?)",
            condominiumId, username, id, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, username, PASSWORD);
    }
}
