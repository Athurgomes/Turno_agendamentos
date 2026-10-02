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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * F8-2 (RF-DAS-03, D-61/D-62): `GET /exports/{type}` para `reservations`,
 * `areas`, `reports`, `payments`, `units`, `format = csv|xlsx`. "Agora" fixo =
 * 2026-11-10 (mesma convenção de {@code DashboardMetricsControllerTest});
 * dados inseridos via JDBC direto, como em toda a suíte de `dashboard`.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class ExportControllerTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";
    private static final String ZONE = "America/Sao_Paulo";
    private static final String PERIOD_FROM = "2026-11-01";
    private static final String PERIOD_TO = "2026-11-30";

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
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Export " + UUID.randomUUID());
        adminToken = createAdmin();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "a-1");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitToken = createUnitAccount(unitId, "a-1");
        areaId = createArea();
    }

    @Test
    @DisplayName("RF-DAS-03: reservations em CSV tem BOM, separador ';' e cabecalhos em pt-BR na ordem do contrato")
    void reservationsCsvHasBomSemicolonAndHeaders() throws Exception {
        insertReservation("CONFIRMED", null, "2026-11-15T09:00:00Z", "2026-11-15T10:00:00Z", true,
            new BigDecimal("120.00"), "Ana Souza");

        MockHttpServletResponse response = mockMvc.perform(get("/api/v1/exports/reservations")
                .param("from", PERIOD_FROM).param("to", PERIOD_TO).param("format", "csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"turno-reservations-" + PERIOD_FROM + "-a-" + PERIOD_TO + ".csv\""))
            .andReturn().getResponse();

        byte[] bytes = response.getContentAsByteArray();
        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
        List<String> lines = csvLines(bytes);
        assertThat(lines.get(0)).isEqualTo("Protocolo;Tipo;Área;Unidade;Responsável;Data;Início;Fim;Convidados;"
            + "Status;Cancelada por;Motivo;Valor");
        String[] cols = lines.get(1).split(";", -1);
        assertThat(cols[1]).isEqualTo("Reserva");
        assertThat(cols[3]).isEqualTo("a-1");
        assertThat(cols[4]).isEqualTo("Ana Souza");
        assertThat(cols[5]).isEqualTo("15/11/2026");
        assertThat(cols[6]).isEqualTo("06:00");
        assertThat(cols[9]).isEqualTo("Confirmada");
        assertThat(cols[12]).isEqualTo("120,00");
    }

    @Test
    @DisplayName("vibe-security/RNF-01: nome de morador comecando com '=' sai neutralizado no CSV (injecao de formula)")
    void formulaInjectionIsNeutralizedInCsv() throws Exception {
        insertReservation("CONFIRMED", null, "2026-11-16T09:00:00Z", "2026-11-16T10:00:00Z", false, null,
            "=cmd|calc");

        byte[] bytes = mockMvc.perform(get("/api/v1/exports/reservations")
                .param("from", PERIOD_FROM).param("to", PERIOD_TO).param("format", "csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        List<String> lines = csvLines(bytes);
        String[] cols = lines.get(1).split(";", -1);
        assertThat(cols[4]).isEqualTo("'=cmd|calc");
    }

    @Test
    @DisplayName("RF-DAS-03: areas em XLSX tem planilha unica, cabecalho e celulas tipadas (numero e data reaproveitando /dashboard/areas)")
    void areasXlsxHasTypedCells() throws Exception {
        insertReservation("CONFIRMED", null, "2026-11-15T09:00:00Z", "2026-11-15T11:00:00Z", true,
            new BigDecimal("150.00"), "Ana Souza");

        byte[] bytes = mockMvc.perform(get("/api/v1/exports/areas")
                .param("from", "2026-11-15").param("to", "2026-11-15").param("format", "xlsx")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .string(HttpHeaders.CONTENT_TYPE,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            XSSFSheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("Área");
            int fontIndex = header.getCell(0).getCellStyle().getFontIndexAsInt();
            assertThat(workbook.getFontAt(fontIndex).getBold()).isTrue();
            assertThat(header.getCell(6).getStringCellValue()).isEqualTo("Ocupação (%)");
            Row dataRow = sheet.getRow(1);
            assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("Disponível");
            assertThat(dataRow.getCell(3).getNumericCellValue()).isEqualTo(1.0);
            assertThat(dataRow.getCell(4).getNumericCellValue()).isEqualTo(2.0);
        }
    }

    @Test
    @DisplayName("RF-DAS-03: reports em CSV tem cabecalhos e categoria/status em pt-BR")
    void reportsCsvHasPortugueseLabels() throws Exception {
        UUID reservationId = insertReservation("CONFIRMED", null, "2026-11-12T09:00:00Z", "2026-11-12T10:00:00Z",
            false, null, "Ana Souza");
        insertReport(reservationId, "MALFUNCTION", "RESOLVED", "2026-11-12T12:00:00Z", "2026-11-13T12:00:00Z",
            new BigDecimal("80.00"));

        byte[] bytes = mockMvc.perform(get("/api/v1/exports/reports")
                .param("from", PERIOD_FROM).param("to", PERIOD_TO).param("format", "csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        List<String> lines = csvLines(bytes);
        assertThat(lines.get(0)).isEqualTo("Protocolo;Área;Unidade;Categoria;Status;Aberto em;Resolvido em;"
            + "Custo de manutenção");
        String[] cols = lines.get(1).split(";", -1);
        assertThat(cols[3]).isEqualTo("Mau funcionamento");
        assertThat(cols[4]).isEqualTo("Resolvido");
        assertThat(cols[7]).isEqualTo("80,00");
    }

    @Test
    @DisplayName("RF-DAS-03: payments em CSV traz so reservas com cobranca no periodo")
    void paymentsCsvOnlyChargedReservations() throws Exception {
        insertReservation("CONFIRMED", null, "2026-11-18T09:00:00Z", "2026-11-18T10:00:00Z", true,
            new BigDecimal("200.00"), "Ana Souza");
        insertReservation("CONFIRMED", null, "2026-11-19T09:00:00Z", "2026-11-19T10:00:00Z", false, null,
            "Ana Souza");

        byte[] bytes = mockMvc.perform(get("/api/v1/exports/payments")
                .param("from", PERIOD_FROM).param("to", PERIOD_TO).param("format", "csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        List<String> lines = csvLines(bytes);
        assertThat(lines.get(0)).isEqualTo("Protocolo;Área;Unidade;Data;Valor;Status;Pagamento confirmado em");
        assertThat(lines).hasSize(2); // cabecalho + 1 linha (so a reserva com cobranca).
        assertThat(lines.get(1)).contains("200,00");
    }

    @Test
    @DisplayName("RF-DAS-03: units em CSV traz bloco, numero, ativa e contagens da unidade")
    void unitsCsvHasCountsAndActiveFlag() throws Exception {
        insertReservation("CONFIRMED", null, "2026-11-20T09:00:00Z", "2026-11-20T10:00:00Z", false, null,
            "Ana Souza");

        byte[] bytes = mockMvc.perform(get("/api/v1/exports/units")
                .param("from", PERIOD_FROM).param("to", PERIOD_TO).param("format", "csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsByteArray();

        List<String> lines = csvLines(bytes);
        assertThat(lines.get(0)).isEqualTo("Unidade;Bloco;Número;Ativa;Moradores ativos;Reservas no período");
        String[] cols = lines.get(1).split(";", -1);
        assertThat(cols[0]).isEqualTo("a-1");
        assertThat(cols[3]).isEqualTo("Sim");
        assertThat(cols[4]).isEqualTo("1");
        assertThat(cols[5]).isEqualTo("1");
    }

    @Test
    @DisplayName("RNF-01: nenhuma exportacao traz colunas de CPF, telefone ou e-mail")
    void noExportHasCpfPhoneOrEmailColumns() throws Exception {
        for (String type : List.of("reservations", "areas", "reports", "payments", "units")) {
            byte[] bytes = mockMvc.perform(get("/api/v1/exports/" + type)
                    .param("from", PERIOD_FROM).param("to", PERIOD_TO).param("format", "csv")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
            String header = csvLines(bytes).get(0).toLowerCase();
            assertThat(header).doesNotContain("cpf").doesNotContain("telefone").doesNotContain("e-mail")
                .doesNotContain("email");
        }
    }

    @Test
    @DisplayName("RF-DAS-03: type invalido retorna 400 VALIDATION_ERROR")
    void invalidTypeIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/exports/invalido").param("format", "csv")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RF-DAS-03: format invalido retorna 400 VALIDATION_ERROR")
    void invalidFormatIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/exports/reservations").param("format", "pdf")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RN-01: conta UNIT recebe 403 em todos os tipos de exportacao")
    void unitAccountIsForbiddenOnAllExportTypes() throws Exception {
        for (String type : List.of("reservations", "areas", "reports", "payments", "units")) {
            mockMvc.perform(get("/api/v1/exports/" + type).param("format", "csv")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
                .andExpect(status().isForbidden());
        }
    }

    // --- helpers ---

    private static List<String> csvLines(byte[] bytes) {
        String content = new String(bytes, StandardCharsets.UTF_8);
        content = content.replace("﻿", ""); // remove o BOM antes de dividir em linhas.
        return List.of(content.split("\r\n")).stream().filter(line -> !line.isBlank()).toList();
    }

    private static Instant instant(String iso) {
        return Instant.parse(iso);
    }

    private UUID insertReservation(String status, String cancelledBy, String startIso, String endIso,
        boolean requiresPayment, BigDecimal price, String residentName) {
        UUID id = UUID.randomUUID();
        Instant startAt = instant(startIso);
        long sequence = jdbcTemplate.queryForObject("select nextval('reservation_code_seq')", Long.class);
        String code = "RES-%d-%06d".formatted(startAt.atZone(ZoneOffset.UTC).getYear(), sequence);
        jdbcTemplate.update("""
            insert into reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id,
                resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status,
                cancelled_by, requires_payment_snapshot, price_snapshot, payment_confirmed_at, created_by)
            values (?, ?, ?, ?, 'BOOKING', ?, ?, ?, '5562999990000', ?, ?, 2, ?, ?, ?, ?, ?, ?)
            """, id, code, condominiumId, areaId, unitId, residentId, residentName, Timestamp.from(startAt),
            Timestamp.from(instant(endIso)), status, cancelledBy, requiresPayment, price,
            requiresPayment && "CONFIRMED".equals(status) ? Timestamp.from(startAt.minusSeconds(3600)) : null,
            adminId);
        return id;
    }

    private void insertReport(UUID reservationId, String category, String status, String createdIso,
        String resolvedIso, BigDecimal maintenanceCost) {
        UUID id = UUID.randomUUID();
        long sequence = jdbcTemplate.queryForObject("select nextval('report_code_seq')", Long.class);
        String code = "OCR-2026-%06d".formatted(sequence);
        jdbcTemplate.update("""
            insert into report (id, code, condominium_id, reservation_id, area_id, unit_id, resident_id,
                resident_name_snapshot, category, description, status, maintenance_cost, resolved_at, created_at)
            values (?, ?, ?, ?, ?, ?, ?, 'Ana Souza', ?, 'Descricao de teste valida', ?, ?, ?, ?)
            """, id, code, condominiumId, reservationId, areaId, unitId, residentId, category, status,
            maintenanceCost, resolvedIso == null ? null : Timestamp.from(instant(resolvedIso)),
            Timestamp.from(instant(createdIso)));
    }

    private UUID createArea() throws Exception {
        String payload = """
            {
              "name": "Salao %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 20,
              "requiresPayment": false,
              "openingHours": [
                { "dayOfWeek": 1, "openTime": "08:00", "closeTime": "18:00" },
                { "dayOfWeek": 2, "openTime": "08:00", "closeTime": "18:00" },
                { "dayOfWeek": 3, "openTime": "08:00", "closeTime": "18:00" },
                { "dayOfWeek": 4, "openTime": "08:00", "closeTime": "18:00" },
                { "dayOfWeek": 5, "openTime": "08:00", "closeTime": "18:00" },
                { "dayOfWeek": 6, "openTime": "08:00", "closeTime": "18:00" },
                { "dayOfWeek": 7, "openTime": "08:00", "closeTime": "18:00" }
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

    private String createAdmin() throws Exception {
        String adminEmail = "admin.export." + UUID.randomUUID() + "@exemplo.test";
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
