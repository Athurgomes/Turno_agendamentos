package br.com.reservas.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.TestImages;
import br.com.reservas.reservation.support.ReservationFixtures;
import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * `/blocks` (S/A, RF-RES-10/RN-33/D-49): criação e remoção de bloqueios de
 * agenda (evento do condomínio, manutenção pontual).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class BlockControllerTest extends AbstractIntegrationTest {

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

    private UUID condominiumId;
    private String adminToken;
    private String syndicToken;
    private UUID unitId;
    private UUID residentId;
    private String unitToken;
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Bloqueios");
        adminToken = createAdmin();
        syndicToken = createSyndic();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "A-101");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitToken = createUnitAccount(unitId, "a-101");
        areaId = createArea(20);
    }

    @Test
    @DisplayName("RF-RES-10/RN-33: SYNDIC cria bloqueio com motivo")
    void syndicCreatesBlock() throws Exception {
        String response = mockMvc.perform(post("/api/v1/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .content(blockPayload(TOMORROW, "14:00", "16:00", "Assembleia de condomínio")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.kind").value("BLOCK"))
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.notes").value("Assembleia de condomínio"))
            .andExpect(jsonPath("$.unitId").doesNotExist())
            .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("RES-\\d{4}-\\d{6}")))
            .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("id").asText()).isNotBlank();
    }

    @Test
    @DisplayName("RN-33/RN-24: bloqueio sobre reserva ativa -> 409 RESERVATION_OVERLAP")
    void blockOverlappingActiveReservationIsRejected() throws Exception {
        createReservation(TOMORROW, "14:00", "16:00");

        mockMvc.perform(post("/api/v1/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content(blockPayload(TOMORROW, "14:00", "16:00", "Manutencao emergencial")))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_OVERLAP"));
    }

    @Test
    @DisplayName("D-49: bloqueio em área em manutenção (não ACTIVE) é aceito, sem exigir RN-18")
    void blockAcceptedInMaintenanceArea() throws Exception {
        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"status\": \"MAINTENANCE\" }"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content(blockPayload(TOMORROW, "14:00", "16:00", "Manutencao preventiva")))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RN-19/RN-33: bloqueio fora do horário de múltiplos de 30 minutos -> 422 INVALID_SLOT")
    void blockWithInvalidSlotIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content(blockPayload(TOMORROW, "14:10", "16:00", "Horario invalido")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_SLOT"));
    }

    @Test
    @DisplayName("vibe-security/RN-01: conta UNIT não cria bloqueio (403)")
    void unitCannotCreateBlock() throws Exception {
        mockMvc.perform(post("/api/v1/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .content(blockPayload(TOMORROW, "14:00", "16:00", "Tentativa indevida")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("vibe-security/RN-01: conta UNIT não remove bloqueio (403)")
    void unitCannotDeleteBlock() throws Exception {
        UUID blockId = createBlock(adminToken, TOMORROW, "14:00", "16:00", "Assembleia");

        mockMvc.perform(delete("/api/v1/blocks/" + blockId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-49: SYNDIC remove bloqueio -> cancelledBy ADMIN, evento com o sindico")
    void syndicDeletesBlockCancelledByAdminButEventKeepsRealActor() throws Exception {
        UUID blockId = createBlock(syndicToken, TOMORROW, "14:00", "16:00", "Assembleia de condominio");

        mockMvc.perform(delete("/api/v1/blocks/" + blockId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reservations/" + blockId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledBy").value("ADMIN"));

        String eventsResponse = mockMvc.perform(get("/api/v1/reservations/" + blockId + "/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode events = objectMapper.readTree(eventsResponse);
        assertThat(events).hasSize(2);
        assertThat(events.get(1).get("type").asText()).isEqualTo("CANCELLED");
        assertThat(events.get(1).get("actor").get("role").asText()).isEqualTo("SYNDIC");
    }

    @Test
    @DisplayName("D-49: remover bloqueio ja cancelado -> 409 INVALID_STATUS_TRANSITION")
    void deletingAlreadyCancelledBlockIsRejected() throws Exception {
        UUID blockId = createBlock(adminToken, TOMORROW, "14:00", "16:00", "Assembleia");
        mockMvc.perform(delete("/api/v1/blocks/" + blockId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/v1/blocks/" + blockId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("D-49: remover reserva (kind BOOKING) pela rota de bloqueios -> 404 NOT_FOUND")
    void deletingABookingAsBlockIsNotFound() throws Exception {
        UUID reservationId = createReservation(TOMORROW, "14:00", "16:00");

        mockMvc.perform(delete("/api/v1/blocks/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("RN-33: bloqueio aparece na agenda (GET /reservations?kind=BLOCK)")
    void blockAppearsInAgendaFilteredByKind() throws Exception {
        createBlock(adminToken, TOMORROW, "14:00", "16:00", "Assembleia");
        createReservation(TOMORROW, "17:00", "18:00");

        mockMvc.perform(get("/api/v1/reservations").param("kind", "BLOCK")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].kind").value("BLOCK"));
    }

    @Test
    @DisplayName("RN-33: para o morador, o bloqueio aparece só como indisponível (sem dados administrativos)")
    void residentSeesBlockAsBusyOnly() throws Exception {
        createBlock(adminToken, TOMORROW, "14:00", "16:00", "Assembleia");

        mockMvc.perform(get("/api/v1/areas/" + areaId + "/availability")
                .param("from", TOMORROW.toString()).param("to", TOMORROW.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].busy[0].kind").value("BLOCK"))
            .andExpect(jsonPath("$[0].busy[0].startTime").value("14:00"))
            .andExpect(jsonPath("$[0].busy[0].reservationId").doesNotExist())
            .andExpect(jsonPath("$[0].busy[0].code").doesNotExist())
            .andExpect(jsonPath("$[0].busy[0].status").doesNotExist());
    }

    private UUID createBlock(String token, LocalDate date, String startTime, String endTime, String reason)
        throws Exception {
        String response = mockMvc.perform(post("/api/v1/blocks")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(blockPayload(date, startTime, endTime, reason)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String blockPayload(LocalDate date, String startTime, String endTime, String reason) {
        return """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "reason": "%s" }
            """.formatted(areaId, date, startTime, endTime, reason);
    }

    private UUID createReservation(LocalDate date, String startTime, String endTime) throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": 2 }
            """.formatted(areaId, date, startTime, endTime, residentId);
        String response = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .content(payload))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("reservation").get("id").asText());
    }

    private UUID createArea(int capacity) throws Exception {
        String payload = """
            {
              "name": "Area %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": %d,
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
            """.formatted(UUID.randomUUID(), capacity);

        var request = multipart("/api/v1/areas")
            .file(new org.springframework.mock.web.MockMultipartFile("data", "data", "application/json",
                payload.getBytes()))
            .file(new org.springframework.mock.web.MockMultipartFile("photos", "foto.png", "image/png",
                TestImages.png()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        String response = mockMvc.perform(request)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String createAdmin() throws Exception {
        String email = "admin.bloqueios." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, email, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, email, PASSWORD);
    }

    private String createSyndic() throws Exception {
        String email = "sindico.bloqueios." + UUID.randomUUID() + "@exemplo.test";
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
