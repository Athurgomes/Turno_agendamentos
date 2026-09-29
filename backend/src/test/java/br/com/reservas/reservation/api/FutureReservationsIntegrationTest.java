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
 * Cancelamento em lote com o gateway/porta reais (F4-6): RN-16 (mudar status
 * ou excluir área) e P-11 (desativar/transferir unidade) com reservas futuras
 * ativas de verdade — sem os fakes de {@code area.support}/dublês, que só
 * simulam o comportamento (D-44).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class FutureReservationsIntegrationTest extends AbstractIntegrationTest {

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
    private UUID unitId;
    private UUID residentId;
    private String unitToken;
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Lote");
        adminToken = createAdmin();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "A-101");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitToken = createUnitAccount(unitId, "a-101");
        areaId = createArea();
    }

    @Test
    @DisplayName("RN-16/D-44: mudar área para MAINTENANCE sem confirmar -> 409 com a reserva futura na lista")
    void areaStatusChangeWithoutConfirmationListsAffectedReservation() throws Exception {
        UUID reservationId = createReservation(TOMORROW, "14:00", "16:00");

        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"status\": \"MAINTENANCE\" }"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("AREA_HAS_FUTURE_RESERVATIONS"))
            .andExpect(jsonPath("$.affectedReservations.length()").value(1))
            .andExpect(jsonPath("$.affectedReservations[0].id").value(reservationId.toString()));
    }

    @Test
    @DisplayName("RN-16: mudar área para MAINTENANCE com confirmação cancela a reserva em lote com o motivo")
    void areaStatusChangeWithConfirmationCancelsReservation() throws Exception {
        UUID reservationId = createReservation(TOMORROW, "14:00", "16:00");

        mockMvc.perform(patch("/api/v1/areas/" + areaId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content("{ \"status\": \"MAINTENANCE\", \"justification\": \"Reforma estrutural na area\", "
                    + "\"confirmCancelAffected\": true }"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.cancelledReservations").value(1));

        mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledBy").value("ADMIN"))
            .andExpect(jsonPath("$.statusReason").value("Reforma estrutural na area"));
    }

    @Test
    @DisplayName("P-11: desativar unidade com reserva futura ativa -> 409 UNIT_HAS_FUTURE_RESERVATIONS")
    void deactivateUnitWithFutureReservationIsRejected() throws Exception {
        UUID reservationId = createReservation(TOMORROW, "14:00", "16:00");

        mockMvc.perform(delete("/api/v1/units/" + unitId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("UNIT_HAS_FUTURE_RESERVATIONS"))
            .andExpect(jsonPath("$.affectedReservations.length()").value(1))
            .andExpect(jsonPath("$.affectedReservations[0].id").value(reservationId.toString()));
    }

    @Test
    @DisplayName("P-11: transferir unidade com reserva futura ativa devolve a lista, sem cancelar nada")
    void transferUnitReturnsAffectedReservations() throws Exception {
        createReservation(TOMORROW, "14:00", "16:00");

        String payload = """
            { "primary": { "name": "Novo Morador", "phone": "5562999991111", "email": "novo@exemplo.test", \
            "cpf": "98765432100" }, "members": [] }
            """;
        mockMvc.perform(post("/api/v1/units/" + unitId + "/transfer")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .content(payload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.affectedReservations.length()").value(1))
            .andExpect(jsonPath("$.affectedReservations[0].status").value("CONFIRMED"));

        // ADMIN decide cancelar cada reserva pela rota de reservas (RN-10): a transferência
        // por si só não mexe no status da reserva listada.
        mockMvc.perform(get("/api/v1/reservations").param("unitId", unitId.toString())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("CONFIRMED"));
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
        String email = "admin.lote." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
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
