package br.com.reservas.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * F4-5: cancelamento pelo morador (RN-30), alteração/cancelamento pelo ADMIN
 * (RF-RES-08, RN-27/RN-28), agenda e histórico S/A (RF-RES-07/09, D-53).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class ReservationLifecycleControllerTest extends AbstractIntegrationTest {

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
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Reservas Ciclo");
        adminToken = createAdmin();
        syndicToken = createSyndic();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "A-101");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitToken = createUnitAccount(unitId, "a-101");
        areaId = createArea(20);
    }

    @Test
    @DisplayName("RN-30: morador cancela a propria reserva 25h antes do inicio")
    void residentCancelsWithinDeadline() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(post("/api/v1/me/reservations/" + reservationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledBy").value("RESIDENT"))
            .andExpect(jsonPath("$.statusReason").value("Cancelada pelo morador"))
            .andExpect(jsonPath("$.canCancel").value(false));
    }

    @Test
    @DisplayName("RN-30: 23h antes do inicio -> 422 CANCEL_DEADLINE_PASSED")
    void residentCancelAfterDeadlineIsRejected() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "09:00", "10:00");

        mockMvc.perform(post("/api/v1/me/reservations/" + reservationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("CANCEL_DEADLINE_PASSED"));
    }

    @Test
    @DisplayName("RN-01/vibe-security: morador nao cancela reserva de outra unidade (403 FORBIDDEN_RESOURCE)")
    void residentCannotCancelAnotherUnitsReservation() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");
        UUID otherUnitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "B-202");
        String otherUnitToken = createUnitAccount(otherUnitId, "b-202");

        mockMvc.perform(post("/api/v1/me/reservations/" + reservationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUnitToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    @DisplayName("RN-32: cancelar uma reserva ja cancelada -> 409 INVALID_STATUS_TRANSITION")
    void cancellingTwiceIsRejected() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");
        mockMvc.perform(post("/api/v1/me/reservations/" + reservationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/reservations/" + reservationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    @DisplayName("RN-27: ADMIN cancela sem justificativa suficiente -> 422 JUSTIFICATION_REQUIRED")
    void adminCancelWithShortJustificationIsRejected() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"justification\": \"curta\" }"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("JUSTIFICATION_REQUIRED"));
    }

    @Test
    @DisplayName("RF-RES-08/RN-27: ADMIN cancela reserva com justificativa valida")
    void adminCancelsWithJustification() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"justification\": \"Area em manutencao urgente\" }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledBy").value("ADMIN"))
            .andExpect(jsonPath("$.statusReason").value("Area em manutencao urgente"))
            .andExpect(jsonPath("$.unitIdentifier").value("A-101"))
            .andExpect(jsonPath("$.whatsappContactUrl").value("https://wa.me/5562999990000"));
    }

    @Test
    @DisplayName("D-16: SYNDIC nao cancela reserva pela rota do ADMIN (403)")
    void syndicCannotCancel() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(post("/api/v1/reservations/" + reservationId + "/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .content("{ \"justification\": \"Area em manutencao urgente\" }"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("D-16: SYNDIC nao altera reserva (403)")
    void syndicCannotUpdate() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(put("/api/v1/reservations/" + reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + syndicToken)
                .content(("{ \"startTime\": \"13:00\", \"endTime\": \"14:00\", "
                    + "\"justification\": \"Troca de horario solicitada\" }")))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-24: ADMIN altera reserva para horario ja ocupado -> 409 RESERVATION_OVERLAP")
    void adminUpdateToOverlappingSlotIsRejected() throws Exception {
        createReservation(unitToken, TOMORROW, "14:00", "16:00");
        UUID reservationId = createReservation(unitToken, TOMORROW, "17:00", "18:00");

        mockMvc.perform(put("/api/v1/reservations/" + reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"startTime\": \"14:00\", \"endTime\": \"16:00\", "
                    + "\"justification\": \"Tentativa de conflito\" }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_OVERLAP"));
    }

    @Test
    @DisplayName("RN-28: ADMIN altera reserva para amanha as 17h sem revalidar a janela do dia seguinte")
    void adminUpdateSkipsAdvanceWindow() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(put("/api/v1/reservations/" + reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content(("{ \"date\": \"" + TOMORROW + "\", \"startTime\": \"17:00\", \"endTime\": \"18:00\", "
                    + "\"justification\": \"Ajuste de horario a pedido do morador\" }")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.startTime").value("17:00"))
            .andExpect(jsonPath("$.statusReason").value("Ajuste de horario a pedido do morador"));
    }

    @Test
    @DisplayName("D-53: agenda filtra por unitIdentifier sem diferenciar maiusculas")
    void agendaFiltersByUnitIdentifierCaseInsensitive() throws Exception {
        createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(get("/api/v1/reservations").param("unitIdentifier", "A-101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/reservations").param("unitIdentifier", "a-101")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1));

        mockMvc.perform(get("/api/v1/reservations").param("unitIdentifier", "z-999")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(0));
    }

    @Test
    @DisplayName("D-49: GET /reservations sem `sort` ordena por inicio (startAt) crescente")
    void agendaDefaultsToStartAtAscendingWhenNoSortIsGiven() throws Exception {
        createReservation(unitToken, TOMORROW, "17:00", "18:00");
        createReservation(unitToken, TOMORROW.plusDays(1), "09:00", "10:00");
        createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(get("/api/v1/reservations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(3))
            .andExpect(jsonPath("$.content[0].startTime").value("11:00"))
            .andExpect(jsonPath("$.content[1].startTime").value("17:00"))
            .andExpect(jsonPath("$.content[2].date").value(TOMORROW.plusDays(1).toString()));
    }

    @Test
    @DisplayName("PUT /reservations/{id} sem `justification` -> 400 VALIDATION_ERROR (constraint estrutural)")
    void updateWithoutJustificationIsRejectedStructurally() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(put("/api/v1/reservations/" + reservationId)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"startTime\": \"13:00\", \"endTime\": \"14:00\" }"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("RN-01: conta UNIT nao acessa a agenda completa (403)")
    void unitCannotAccessAgenda() throws Exception {
        mockMvc.perform(get("/api/v1/reservations").header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-01: conta UNIT nao ve o detalhe de reserva de outra unidade (403 FORBIDDEN_RESOURCE)")
    void unitCannotSeeAnotherUnitsDetail() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");
        UUID otherUnitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "C-303");
        String otherUnitToken = createUnitAccount(otherUnitId, "c-303");

        mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherUnitToken))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN_RESOURCE"));
    }

    @Test
    @DisplayName("RF-RES-05/06: conta UNIT ve o detalhe da propria reserva")
    void unitSeesOwnDetail() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(reservationId.toString()))
            .andExpect(jsonPath("$.unitId").doesNotExist());
    }

    @Test
    @DisplayName("RF-RES-07: S/A ve o detalhe com dados de unidade e contato")
    void adminSeesDetailWithUnitAndContact() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");

        mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unitIdentifier").value("A-101"))
            .andExpect(jsonPath("$.whatsappContactUrl").value("https://wa.me/5562999990000"));
    }

    @Test
    @DisplayName("RF-RES-09: historico registra CREATED e CANCELLED com o ator correto")
    void eventsRecordCreatedAndCancelled() throws Exception {
        UUID reservationId = createReservation(unitToken, TOMORROW, "11:00", "12:00");
        mockMvc.perform(post("/api/v1/me/reservations/" + reservationId + "/cancel")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk());

        String response = mockMvc.perform(get("/api/v1/reservations/" + reservationId + "/events")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        JsonNode events = objectMapper.readTree(response);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("type").asText()).isEqualTo("CREATED");
        assertThat(events.get(1).get("type").asText()).isEqualTo("CANCELLED");
        assertThat(events.get(1).get("justification").asText()).isEqualTo("Cancelada pelo morador");
        assertThat(events.get(1).get("actor").get("role").asText()).isEqualTo("UNIT");
        assertThat(events.get(1).get("actor").get("name").asText()).isEqualTo("A-101");
    }

    private UUID createReservation(String token, LocalDate date, String startTime, String endTime) throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": 2 }
            """.formatted(areaId, date, startTime, endTime, residentId);
        String response = mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
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
        String email = "admin.reservas.ciclo." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, email, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, email, PASSWORD);
    }

    private String createSyndic() throws Exception {
        String email = "sindico.reservas.ciclo." + UUID.randomUUID() + "@exemplo.test";
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
