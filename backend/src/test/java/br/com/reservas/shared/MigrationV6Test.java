package br.com.reservas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.support.AbstractIntegrationTest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida o resultado da migration V6 direto no PostgreSQL real: a exclusion
 * constraint anti-sobreposicao de reservation (RN-24), o check de BOOKING
 * (D-08), o codigo de protocolo (D-49) e o SQLState 23P01 devolvido em
 * conflitos, checado diretamente aqui pois e o proprio contrato da constraint
 * (a traducao para 409 RESERVATION_OVERLAP fica na camada de aplicacao).
 */
@SpringBootTest
@Transactional
class MigrationV6Test extends AbstractIntegrationTest {

    private static final String OVERLAP_SQLSTATE = "23P01";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Extrai o SQLState do {@link java.sql.SQLException} raiz de uma excecao do JDBC. */
    private static String rootSqlState(Throwable ex) {
        Throwable cause = ex;
        while (cause != null && !(cause instanceof java.sql.SQLException)) {
            cause = cause.getCause();
        }
        return cause == null ? null : ((java.sql.SQLException) cause).getSQLState();
    }

    private UUID condominiumId() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO condominium (id, name) VALUES (?, ?)", id, "Condominio Ficticio Teste");
        return id;
    }

    private UUID adminId(UUID condominiumId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO user_account (id, condominium_id, role, email, password_hash) "
                + "VALUES (?, ?, 'ADMIN', ?, 'hash')",
            id, condominiumId, "admin" + id + "@exemplo.test");
        return id;
    }

    private UUID areaId(UUID condominiumId, boolean requiresPayment) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO common_area (id, condominium_id, name, category, description, rules, "
                + "conduct_guidelines, capacity, requires_payment, price, payment_whatsapp) "
                + "VALUES (?, ?, 'Salao de Festas', 'PARTY_ROOM', 'desc', 'rules', 'conduct', 10, ?, ?, ?)",
            id, condominiumId, requiresPayment,
            requiresPayment ? new java.math.BigDecimal("50.00") : null,
            requiresPayment ? "5562999998888" : null);
        return id;
    }

    private UUID unitId(UUID condominiumId, String identifier) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO unit (id, condominium_id, number, identifier) VALUES (?, ?, ?, ?)",
            id, condominiumId, identifier, identifier);
        return id;
    }

    private UUID residentId(UUID unitId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO resident (id, unit_id, name, phone, email, cpf, is_primary) "
                + "VALUES (?, ?, 'Morador Principal', '5562999998888', 'morador@exemplo.test', "
                + "'11144477735', true)",
            id, unitId);
        return id;
    }

    private UUID insertBooking(UUID condominiumId, UUID areaId, UUID unitId, UUID residentId, UUID createdBy,
            String code, Instant start, Instant end, String status) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id, "
                + "resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status, "
                + "created_by) "
                + "VALUES (?, ?, ?, ?, 'BOOKING', ?, ?, 'Morador Principal', '5562999998888', ?, ?, 2, ?, ?)",
            id, code, condominiumId, areaId, unitId, residentId, java.sql.Timestamp.from(start),
            java.sql.Timestamp.from(end), status, createdBy);
        return id;
    }

    private void insertBlock(UUID condominiumId, UUID areaId, UUID createdBy, String code, Instant start,
            Instant end, String status) {
        jdbcTemplate.update(
            "INSERT INTO reservation (code, condominium_id, area_id, kind, start_at, end_at, status, created_by) "
                + "VALUES (?, ?, ?, 'BLOCK', ?, ?, ?, ?)",
            code, condominiumId, areaId, java.sql.Timestamp.from(start), java.sql.Timestamp.from(end), status,
            createdBy);
    }

    @Test
    @DisplayName("RN-24: reservas adjacentes (14:00-16:00 e 16:00-18:00) na mesma area nao se sobrepoem")
    void adjacentBookingsInSameAreaSucceed() {
        UUID condominiumId = condominiumId();
        UUID areaId = areaId(condominiumId, false);
        UUID unitId = unitId(condominiumId, "101a");
        UUID residentId = residentId(unitId);
        UUID createdBy = adminId(condominiumId);
        Instant base = Instant.parse("2026-10-05T14:00:00Z");

        insertBooking(condominiumId, areaId, unitId, residentId, createdBy, "RES-2026-000001", base,
            base.plus(2, ChronoUnit.HOURS), "PENDING_PAYMENT");
        insertBooking(condominiumId, areaId, unitId, residentId, createdBy, "RES-2026-000002",
            base.plus(2, ChronoUnit.HOURS), base.plus(4, ChronoUnit.HOURS), "PENDING_PAYMENT");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM reservation WHERE area_id = ?", Integer.class, areaId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-24: reservas sobrepostas (14:00-16:00 e 15:00-17:00) na mesma area violam a exclusion "
        + "constraint com SQLState 23P01")
    void overlappingBookingsInSameAreaFailWithSqlState23P01() {
        UUID condominiumId = condominiumId();
        UUID areaId = areaId(condominiumId, false);
        UUID unitId = unitId(condominiumId, "101a");
        UUID residentId = residentId(unitId);
        UUID createdBy = adminId(condominiumId);
        Instant base = Instant.parse("2026-10-05T14:00:00Z");
        insertBooking(condominiumId, areaId, unitId, residentId, createdBy, "RES-2026-000001", base,
            base.plus(2, ChronoUnit.HOURS), "PENDING_PAYMENT");

        assertThatThrownBy(() -> insertBooking(condominiumId, areaId, unitId, residentId, createdBy,
                "RES-2026-000002", base.plus(1, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS),
                "PENDING_PAYMENT"))
            .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                ex -> assertThat(rootSqlState(ex)).isEqualTo(OVERLAP_SQLSTATE));
    }

    @Test
    @DisplayName("RN-24: mesmo horario em areas diferentes nao viola a exclusion constraint")
    void overlappingBookingsInDifferentAreasSucceed() {
        UUID condominiumId = condominiumId();
        UUID areaId1 = areaId(condominiumId, false);
        UUID areaId2 = areaId(condominiumId, false);
        UUID unitId = unitId(condominiumId, "101a");
        UUID residentId = residentId(unitId);
        UUID createdBy = adminId(condominiumId);
        Instant base = Instant.parse("2026-10-05T14:00:00Z");
        insertBooking(condominiumId, areaId1, unitId, residentId, createdBy, "RES-2026-000001", base,
            base.plus(2, ChronoUnit.HOURS), "PENDING_PAYMENT");

        insertBooking(condominiumId, areaId2, unitId, residentId, createdBy, "RES-2026-000002", base,
            base.plus(2, ChronoUnit.HOURS), "PENDING_PAYMENT");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-24: sobreposicao com uma reserva CANCELLED nao viola a exclusion constraint")
    void overlappingWithCancelledReservationSucceeds() {
        UUID condominiumId = condominiumId();
        UUID areaId = areaId(condominiumId, false);
        UUID unitId = unitId(condominiumId, "101a");
        UUID residentId = residentId(unitId);
        UUID createdBy = adminId(condominiumId);
        Instant base = Instant.parse("2026-10-05T14:00:00Z");
        insertBooking(condominiumId, areaId, unitId, residentId, createdBy, "RES-2026-000001", base,
            base.plus(2, ChronoUnit.HOURS), "CANCELLED");

        insertBooking(condominiumId, areaId, unitId, residentId, createdBy, "RES-2026-000002", base,
            base.plus(2, ChronoUnit.HOURS), "PENDING_PAYMENT");

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM reservation", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-33: bloqueio CONFIRMED sobreposto a reserva PENDING_PAYMENT na mesma area viola a "
        + "exclusion constraint")
    void confirmedBlockOverlappingPendingBookingFails() {
        UUID condominiumId = condominiumId();
        UUID areaId = areaId(condominiumId, false);
        UUID unitId = unitId(condominiumId, "101a");
        UUID residentId = residentId(unitId);
        UUID createdBy = adminId(condominiumId);
        Instant base = Instant.parse("2026-10-05T14:00:00Z");
        insertBooking(condominiumId, areaId, unitId, residentId, createdBy, "RES-2026-000001", base,
            base.plus(2, ChronoUnit.HOURS), "PENDING_PAYMENT");

        assertThatThrownBy(() -> insertBlock(condominiumId, areaId, createdBy, "RES-2026-000002",
                base.plus(1, ChronoUnit.HOURS), base.plus(3, ChronoUnit.HOURS), "CONFIRMED"))
            .isInstanceOfSatisfying(DataIntegrityViolationException.class,
                ex -> assertThat(rootSqlState(ex)).isEqualTo(OVERLAP_SQLSTATE));
    }

    @Test
    @DisplayName("D-08: BOOKING sem unit_id viola o check de reservation")
    void bookingWithoutUnitIdFails() {
        UUID condominiumId = condominiumId();
        UUID areaId = areaId(condominiumId, false);
        UUID createdBy = adminId(condominiumId);
        Instant base = Instant.parse("2026-10-05T14:00:00Z");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO reservation (code, condominium_id, area_id, kind, resident_name_snapshot, "
                    + "start_at, end_at, guests, status, created_by) "
                    + "VALUES (?, ?, ?, 'BOOKING', 'Sem Unidade', ?, ?, 2, 'PENDING_PAYMENT', ?)",
                "RES-2026-000099", condominiumId, areaId, java.sql.Timestamp.from(base),
                java.sql.Timestamp.from(base.plus(2, ChronoUnit.HOURS)), createdBy))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("D-49: reservation_code_seq gera valores sequenciais usados para montar o protocolo RES-{ano}-{6 digitos}")
    void reservationCodeSequenceWorks() {
        Long first = jdbcTemplate.queryForObject("SELECT nextval('reservation_code_seq')", Long.class);
        Long second = jdbcTemplate.queryForObject("SELECT nextval('reservation_code_seq')", Long.class);

        assertThat(second).isEqualTo(first + 1);
    }
}
