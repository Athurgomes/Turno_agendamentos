package br.com.reservas.reservation.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.reservas.auth.support.MutableClock;
import br.com.reservas.reservation.infra.ReservationEventRepository;
import br.com.reservas.reservation.support.ReservationFixtures;
import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-31: teste de unidade do gancho de expiração ({@link ReservationService#expirePendingIfNeeded})
 * no horário exato, com o mesmo {@link Clock} de teste mutável usado pelos demais testes de
 * `reservation` (F5-2: é a mesma chamada que {@code ReservationExpirationJob} faz a cada 15 min).
 * Mesma assinatura `@SpringBootTest @AutoConfigureMockMvc @Import(ReservationTestConfig.class)
 * @Transactional` dos outros testes do módulo para reaproveitar o contexto (e o pool Hikari) já
 * em cache, em vez de abrir mais um (D-37).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@Transactional
class ReservationServiceExpirationTest extends AbstractIntegrationTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationEventRepository events;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Clock clock;

    private UUID reservationId;

    @BeforeEach
    void setUp() {
        UUID condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Expiracao RN-31");
        UUID unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "A-1");
        UUID residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Ana Souza");
        UUID adminId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into user_account (id, condominium_id, role, email, display_name, password_hash) "
                + "values (?, ?, 'ADMIN', ?, 'Admin', 'hash')",
            adminId, condominiumId, "admin.expiracao." + UUID.randomUUID() + "@exemplo.test");
        UUID areaId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into common_area (id, condominium_id, name, category, description, rules, "
                + "conduct_guidelines, capacity, requires_payment, price, payment_whatsapp, status) "
                + "values (?, ?, 'Salao', 'PARTY_ROOM', 'Descricao', 'Regras', 'Conduta', 20, true, 150.00, "
                + "'5562999998888', 'ACTIVE')",
            areaId, condominiumId);

        // Pendente com inicio as 14:00 America/Sao_Paulo (17:00Z).
        reservationId = UUID.randomUUID();
        jdbcTemplate.update(
            "insert into reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id, "
                + "resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status, "
                + "requires_payment_snapshot, price_snapshot, created_by) values (?, ?, ?, ?, 'BOOKING', ?, ?, "
                + "'Ana Souza', '5562999990000', '2026-11-11T17:00:00Z', '2026-11-11T18:00:00Z', 2, "
                + "'PENDING_PAYMENT', true, 150.00, ?)",
            reservationId, "RES-2026-000321", condominiumId, areaId, unitId, residentId, adminId);
    }

    @Test
    @DisplayName("RN-31: as 13:59 (1 min antes do inicio da reserva) continua PENDING_PAYMENT")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void doesNotExpireOneMinuteBeforeStart() {
        advanceTo("2026-11-11T16:59:00Z");

        reservationService.expirePendingIfNeeded(Instant.now(clock));

        assertThat(statusOf()).isEqualTo("PENDING_PAYMENT");
    }

    @Test
    @DisplayName("RN-31: exatamente as 14:00 (inicio da reserva) expira com o motivo exato e o evento EXPIRED")
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
    void expiresExactlyAtStart() {
        advanceTo("2026-11-11T17:00:00Z");

        reservationService.expirePendingIfNeeded(Instant.now(clock));

        assertThat(statusOf()).isEqualTo("CANCELLED");
        assertThat(jdbcTemplate.queryForObject("select status_reason from reservation where id = ?", String.class,
            reservationId)).isEqualTo("Pagamento não confirmado até o início da reserva.");
        assertThat(jdbcTemplate.queryForObject("select cancelled_by from reservation where id = ?", String.class,
            reservationId)).isEqualTo("SYSTEM");

        long expiredEvents = events.findByReservationIdOrderByOccurredAtAsc(reservationId).stream()
            .filter(e -> "EXPIRED".equals(e.getType()) && e.getActorId() == null)
            .count();
        assertThat(expiredEvents).isEqualTo(1);
    }

    private void advanceTo(String instant) {
        Instant target = Instant.parse(instant);
        Duration toAdvance = Duration.between(clock.instant(), target);
        ((MutableClock) clock).advance(toAdvance);
    }

    private String statusOf() {
        return jdbcTemplate.queryForObject("select status from reservation where id = ?", String.class,
            reservationId);
    }
}
