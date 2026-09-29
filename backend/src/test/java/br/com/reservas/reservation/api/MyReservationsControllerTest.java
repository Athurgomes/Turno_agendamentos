package br.com.reservas.reservation.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.AreaTestStorageConfig;
import br.com.reservas.area.support.TestImages;
import br.com.reservas.auth.support.MutableClock;
import br.com.reservas.reservation.support.ReservationFixtures;
import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** `GET /me/reservations` (RF-RES-05, RN-01). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class MyReservationsControllerTest extends AbstractIntegrationTest {

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
    private UUID adminAccountId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio MinhasReservas");
        adminToken = createAdmin();
    }

    @Test
    @DisplayName("RF-RES-05/RN-01: /me/reservations so devolve reservas da propria unidade, por scope")
    void listsOnlyOwnUnitReservationsByScope() throws Exception {
        UUID areaId = createArea(20);

        UUID unitAId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "a-1");
        UUID residentAId = ReservationFixtures.insertResident(jdbcTemplate, unitAId, "Ana Souza");
        String unitAToken = createUnitAccount(unitAId, "a-1");

        UUID unitBId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "b-2");
        UUID residentBId = ReservationFixtures.insertResident(jdbcTemplate, unitBId, "Bruno Lima");
        String unitBToken = createUnitAccount(unitBId, "b-2");

        create(unitAToken, areaId, "2026-11-11", "14:00", "16:00", residentAId);
        create(unitBToken, areaId, "2026-11-11", "16:00", "18:00", residentBId);

        mockMvc.perform(get("/api/v1/me/reservations").param("scope", "upcoming")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitAToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].startTime").value("14:00"));
    }

    @Test
    @DisplayName("RF-RES-05: scope=past so traz reservas com inicio antes de agora, decrescente")
    void pastScopeOnlyReturnsReservationsBeforeNow() throws Exception {
        UUID areaId = createArea(20);
        UUID unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "c-3");
        UUID residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Carla Dias");
        String unitToken = createUnitAccount(unitId, "c-3");

        // Reserva "passada": insercao direta (a API nao permite criar reserva no passado, RN-20).
        UUID reservationId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id, "
                + "resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status, "
                + "requires_payment_snapshot, created_by) values (?, ?, ?, ?, 'BOOKING', ?, ?, 'Carla Dias', "
                + "'5562999990000', '2026-01-01T14:00:00Z', '2026-01-01T16:00:00Z', 2, 'CONFIRMED', false, ?)",
            reservationId, "RES-2026-000999", condominiumId, areaId, unitId, residentId, adminAccountId);

        create(unitToken, areaId, "2026-11-11", "14:00", "16:00", residentId);

        mockMvc.perform(get("/api/v1/me/reservations").param("scope", "past")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.content[0].code").value("RES-2026-000999"))
            .andExpect(jsonPath("$.content[0].completed").value(true));
    }

    private void create(String token, UUID areaId, String date, String startTime, String endTime, UUID residentId)
        throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": 2 }
            """.formatted(areaId, date, startTime, endTime, residentId);
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content(payload))
            .andExpect(status().isCreated());
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
              "price": null,
              "paymentWhatsapp": null,
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
            .file(new MockMultipartFile("data", "data", "application/json", payload.getBytes()))
            .file(new MockMultipartFile("photos", "foto.png", "image/png", TestImages.png()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        String response = mockMvc.perform(request)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String createAdmin() throws Exception {
        String email = "admin.minhasreservas." + UUID.randomUUID() + "@exemplo.test";
        adminAccountId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into user_account (id, condominium_id, role, email, display_name, password_hash) "
                + "values (?, ?, 'ADMIN', ?, 'Admin', ?)",
            adminAccountId, condominiumId, email, passwordEncoder.encode(PASSWORD));
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
