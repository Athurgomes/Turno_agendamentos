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

/** `GET /areas/{id}/availability` (docs/03, F4-4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class AvailabilityControllerTest extends AbstractIntegrationTest {

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
    private UUID areaId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Disponibilidade");
        adminToken = createAdmin();
        areaId = createArea(20);
    }

    @Test
    @DisplayName("F4-4: intervalo maior que 62 dias -> 400 VALIDATION_ERROR")
    void rejectsRangeOverSixtyTwoDays() throws Exception {
        mockMvc.perform(get("/api/v1/areas/{id}/availability", areaId)
                .param("from", "2026-11-10")
                .param("to", "2027-02-11")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("F4-4: para UNIT, `busy` nao traz dado de quem reservou (RN-01/IDOR)")
    void unitViewHidesReservationDetails() throws Exception {
        UUID unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "a-1");
        UUID residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        String unitToken = createUnitAccount(unitId, "a-1");

        String payload = """
            { "areaId": "%s", "date": "2026-11-11", "startTime": "14:00", "endTime": "16:00", \
            "residentId": "%s", "guests": 2 }
            """.formatted(areaId, residentId);
        mockMvc.perform(post("/api/v1/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken)
                .content(payload))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/areas/{id}/availability", areaId)
                .param("from", "2026-11-11")
                .param("to", "2026-11-11")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].busy.length()").value(1))
            .andExpect(jsonPath("$[0].busy[0].startTime").value("14:00"))
            .andExpect(jsonPath("$[0].busy[0].endTime").value("16:00"))
            .andExpect(jsonPath("$[0].busy[0].reservationId").doesNotExist())
            .andExpect(jsonPath("$[0].busy[0].unitIdentifier").doesNotExist())
            .andExpect(jsonPath("$[0].busy[0].status").doesNotExist());

        mockMvc.perform(get("/api/v1/areas/{id}/availability", areaId)
                .param("from", "2026-11-11")
                .param("to", "2026-11-11")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].busy[0].unitIdentifier").value("a-1"))
            .andExpect(jsonPath("$[0].busy[0].status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("F4-4: dia de hoje nao e reservavel (RN-20, SAME_DAY_NOT_ALLOWED)")
    void todayIsNotBookable() throws Exception {
        mockMvc.perform(get("/api/v1/areas/{id}/availability", areaId)
                .param("from", "2026-11-10")
                .param("to", "2026-11-10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].bookable").value(false))
            .andExpect(jsonPath("$[0].notBookableReason").value("SAME_DAY_NOT_ALLOWED"));
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
        String email = "admin.disponibilidade." + UUID.randomUUID() + "@exemplo.test";
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
