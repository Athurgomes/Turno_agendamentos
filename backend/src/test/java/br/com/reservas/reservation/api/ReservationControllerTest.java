package br.com.reservas.reservation.api;

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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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

/** `POST /reservations` (RF-RES-01..04; RN-18..26). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class ReservationControllerTest extends AbstractIntegrationTest {

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

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Reservas");
        adminToken = createAdmin();
        unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "a-1");
        residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        unitToken = createUnitAccount(unitId, "a-1");
    }

    @Test
    @DisplayName("RN-24: 14:00-16:00 e 16:00-18:00 na mesma area nao se sobrepoem (intervalo semiaberto)")
    void acceptsBackToBackSlots() throws Exception {
        UUID areaId = createArea(false, null, null, 20);

        create(unitToken, areaId, TOMORROW, "14:00", "16:00", residentId, 2)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservation.status").value("CONFIRMED"));
        create(unitToken, areaId, TOMORROW, "16:00", "18:00", residentId, 2)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservation.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("RN-24: 14:00-16:00 e 15:00-17:00 na mesma area -> 409 RESERVATION_OVERLAP")
    void rejectsOverlappingSlots() throws Exception {
        UUID areaId = createArea(false, null, null, 20);

        create(unitToken, areaId, TOMORROW, "14:00", "16:00", residentId, 2).andExpect(status().isCreated());
        create(unitToken, areaId, TOMORROW, "15:00", "17:00", residentId, 2)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESERVATION_OVERLAP"));
    }

    @Test
    @DisplayName("RN-25: area gratuita nasce CONFIRMED")
    void freeAreaStartsConfirmed() throws Exception {
        UUID areaId = createArea(false, null, null, 20);

        String response = create(unitToken, areaId, TOMORROW, "09:00", "10:00", residentId, 2)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservation.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.reservation.statusReason").doesNotExist())
            .andExpect(jsonPath("$.whatsappPaymentUrl").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        UUID reservationId = UUID.fromString(objectMapper.readTree(response).get("reservation").get("id").asText());
        String phoneSnapshot = jdbcTemplate.queryForObject(
            "select resident_phone_snapshot from reservation where id = ?", String.class, reservationId);
        org.assertj.core.api.Assertions.assertThat(phoneSnapshot).isEqualTo("5562999990000");
    }

    @Test
    @DisplayName("RN-25/RN-26: area paga nasce PENDING_PAYMENT com whatsappPaymentUrl correta")
    void payingAreaStartsPendingWithWhatsappUrl() throws Exception {
        UUID areaId = createArea(true, "150.00", "5562999998888", 20);

        String response = create(unitToken, areaId, TOMORROW, "19:00", "21:00", residentId, 2)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.reservation.status").value("PENDING_PAYMENT"))
            .andExpect(jsonPath("$.reservation.statusReason")
                .value("Aguardando confirmação de pagamento pela administração."))
            .andExpect(jsonPath("$.whatsappPaymentUrl").exists())
            .andReturn().getResponse().getContentAsString();

        var json = objectMapper.readTree(response);
        String url = json.get("whatsappPaymentUrl").asText();
        org.assertj.core.api.Assertions.assertThat(url).isEqualTo(json.get("reservation").get("whatsappPaymentUrl")
            .asText());
        org.assertj.core.api.Assertions.assertThat(url).startsWith("https://wa.me/5562999998888?text=");
        String decoded = URLDecoder.decode(url.substring(url.indexOf("text=") + 5), StandardCharsets.UTF_8);
        org.assertj.core.api.Assertions.assertThat(decoded)
            .contains("da unidade A-1")
            .contains("11/11/2026, das 19:00 às 21:00")
            .contains("R$ 150,00");
    }

    @Test
    @DisplayName("RN-22: a 4a reserva ativa da unidade e recusada (limite default = 3)")
    void fourthActiveReservationIsRejected() throws Exception {
        UUID areaId = createArea(false, null, null, 20);

        create(unitToken, areaId, TOMORROW, "08:00", "09:00", residentId, 2).andExpect(status().isCreated());
        create(unitToken, areaId, TOMORROW, "09:00", "10:00", residentId, 2).andExpect(status().isCreated());
        create(unitToken, areaId, TOMORROW, "10:00", "11:00", residentId, 2).andExpect(status().isCreated());
        create(unitToken, areaId, TOMORROW, "11:00", "12:00", residentId, 2)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("UNIT_BOOKING_LIMIT_REACHED"));
    }

    @Test
    @DisplayName("RN-22: limite parametrizado em 0 significa sem limite")
    void zeroMeansUnlimitedBookings() throws Exception {
        jdbcTemplate.update("update condominium_settings set max_active_bookings_per_unit = 0 "
            + "where condominium_id = ?", condominiumId);
        UUID areaId = createArea(false, null, null, 20);

        create(unitToken, areaId, TOMORROW, "08:00", "09:00", residentId, 2).andExpect(status().isCreated());
        create(unitToken, areaId, TOMORROW, "09:00", "10:00", residentId, 2).andExpect(status().isCreated());
        create(unitToken, areaId, TOMORROW, "10:00", "11:00", residentId, 2).andExpect(status().isCreated());
        create(unitToken, areaId, TOMORROW, "11:00", "12:00", residentId, 2).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RN-01/vibe-security: UNIT nao consegue reservar usando morador de outra unidade")
    void cannotUseResidentFromAnotherUnit() throws Exception {
        UUID otherUnitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "b-2");
        UUID otherResidentId = ReservationFixtures.insertResident(jdbcTemplate, otherUnitId, "Bruno Lima");
        UUID areaId = createArea(false, null, null, 20);

        create(unitToken, areaId, TOMORROW, "14:00", "16:00", otherResidentId, 2)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, UUID areaId, LocalDate date,
        String startTime, String endTime, UUID residentId, int guests) throws Exception {
        String payload = """
            { "areaId": "%s", "date": "%s", "startTime": "%s", "endTime": "%s", "residentId": "%s", "guests": %d }
            """.formatted(areaId, date, startTime, endTime, residentId, guests);
        return mockMvc.perform(post("/api/v1/reservations")
            .contentType(MediaType.APPLICATION_JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .content(payload));
    }

    private UUID createArea(boolean requiresPayment, String price, String whatsapp, int capacity) throws Exception {
        String payload = """
            {
              "name": "Area %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": %d,
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
            """.formatted(UUID.randomUUID(), capacity, requiresPayment, price == null ? "null" : price,
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
        String email = "admin.reservas." + UUID.randomUUID() + "@exemplo.test";
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
