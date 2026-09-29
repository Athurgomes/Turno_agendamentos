package br.com.reservas.shared;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
 * Valida o resultado da migration V7 direto no PostgreSQL real: os checks de
 * report (RN-35, RN-36, RN-37), o check de stage de report_photo e o codigo de
 * protocolo (D-50).
 */
@SpringBootTest
@Transactional
class MigrationV7Test extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    private UUID areaId(UUID condominiumId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO common_area (id, condominium_id, name, category, description, rules, "
                + "conduct_guidelines, capacity) "
                + "VALUES (?, ?, 'Salao de Festas', 'PARTY_ROOM', 'desc', 'rules', 'conduct', 10)",
            id, condominiumId);
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

    private UUID reservationId(UUID condominiumId, UUID areaId, UUID unitId, UUID residentId, UUID createdBy) {
        UUID id = UUID.randomUUID();
        Instant start = Instant.parse("2026-09-01T14:00:00Z");
        jdbcTemplate.update(
            "INSERT INTO reservation (id, code, condominium_id, area_id, kind, unit_id, resident_id, "
                + "resident_name_snapshot, resident_phone_snapshot, start_at, end_at, guests, status, "
                + "created_by) "
                + "VALUES (?, 'RES-2026-000001', ?, ?, 'BOOKING', ?, ?, 'Morador Principal', "
                + "'5562999998888', ?, ?, 2, 'CONFIRMED', ?)",
            id, condominiumId, areaId, unitId, residentId, Timestamp.from(start),
            Timestamp.from(start.plus(2, ChronoUnit.HOURS)), createdBy);
        return id;
    }

    private record Fixture(UUID condominiumId, UUID areaId, UUID unitId, UUID residentId, UUID adminId,
            UUID reservationId) {
    }

    private Fixture fixture() {
        UUID condominiumId = condominiumId();
        UUID areaId = areaId(condominiumId);
        UUID unitId = unitId(condominiumId, "101a");
        UUID residentId = residentId(unitId);
        UUID adminId = adminId(condominiumId);
        UUID reservationId = reservationId(condominiumId, areaId, unitId, residentId, adminId);
        return new Fixture(condominiumId, areaId, unitId, residentId, adminId, reservationId);
    }

    private void insertReport(Fixture f, String code, String description, String status, String statusReason,
            BigDecimal maintenanceCost) {
        jdbcTemplate.update(
            "INSERT INTO report (code, condominium_id, reservation_id, area_id, unit_id, resident_id, "
                + "resident_name_snapshot, category, description, status, status_reason, maintenance_cost) "
                + "VALUES (?, ?, ?, ?, ?, ?, 'Morador Principal', 'DAMAGE', ?, ?, ?, ?)",
            code, f.condominiumId(), f.reservationId(), f.areaId(), f.unitId(), f.residentId(), description,
            status, statusReason, maintenanceCost);
    }

    @Test
    @DisplayName("RN-35: descricao com menos de 10 caracteres viola o check de report")
    void descriptionShorterThan10CharsFails() {
        Fixture f = fixture();

        assertThatThrownBy(() -> insertReport(f, "OCR-2026-000001", "curta", "OPEN", null, null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-35: descricao com 10 ou mais caracteres e aceita")
    void descriptionWith10CharsSucceeds() {
        Fixture f = fixture();

        insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "OPEN", null, null);

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM report", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-37: custo de manutencao informado com status diferente de RESOLVED viola o check")
    void maintenanceCostWithoutResolvedStatusFails() {
        Fixture f = fixture();

        assertThatThrownBy(() -> insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "IN_MAINTENANCE",
                null, new BigDecimal("50.00")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-37: custo de manutencao negativo viola o check mesmo com status RESOLVED")
    void negativeMaintenanceCostFails() {
        Fixture f = fixture();

        assertThatThrownBy(() -> insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "RESOLVED", null,
                new BigDecimal("-10.00")))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-37: custo de manutencao nao negativo com status RESOLVED e aceito")
    void nonNegativeMaintenanceCostWithResolvedStatusSucceeds() {
        Fixture f = fixture();

        insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "RESOLVED", null, new BigDecimal("50.00"));

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM report", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-36: status DISMISSED sem status_reason viola o check")
    void dismissedWithoutReasonFails() {
        Fixture f = fixture();

        assertThatThrownBy(() -> insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "DISMISSED", null,
                null))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-36: status DISMISSED com status_reason e aceito")
    void dismissedWithReasonSucceeds() {
        Fixture f = fixture();

        insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "DISMISSED", "Duplicado", null);

        Integer count = jdbcTemplate.queryForObject("SELECT count(*) FROM report", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-35: categoria fora do enum viola o check de report")
    void invalidCategoryFails() {
        Fixture f = fixture();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO report (code, condominium_id, reservation_id, area_id, unit_id, resident_id, "
                    + "resident_name_snapshot, category, description, status) "
                    + "VALUES (?, ?, ?, ?, ?, ?, 'Morador Principal', 'INVALID', 'Churrasqueira quebrada', 'OPEN')",
                "OCR-2026-000001", f.condominiumId(), f.reservationId(), f.areaId(), f.unitId(), f.residentId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RF-REP-01: report_photo com stage invalido viola o check")
    void invalidPhotoStageFails() {
        Fixture f = fixture();
        insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "OPEN", null, null);
        UUID reportId = jdbcTemplate.queryForObject(
            "SELECT id FROM report WHERE code = 'OCR-2026-000001'", UUID.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO report_photo (report_id, storage_key, content_type, stage, uploaded_by) "
                    + "VALUES (?, 'key/foto.jpg', 'image/jpeg', 'INVALID', ?)",
                reportId, f.adminId()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RF-REP-01: report_photo com stage REPORTED e REPAIR e aceito")
    void validPhotoStagesSucceed() {
        Fixture f = fixture();
        insertReport(f, "OCR-2026-000001", "Churrasqueira quebrada", "OPEN", null, null);
        UUID reportId = jdbcTemplate.queryForObject(
            "SELECT id FROM report WHERE code = 'OCR-2026-000001'", UUID.class);

        jdbcTemplate.update(
            "INSERT INTO report_photo (report_id, storage_key, content_type, stage, uploaded_by) "
                + "VALUES (?, 'key/foto1.jpg', 'image/jpeg', 'REPORTED', ?)",
            reportId, f.adminId());
        jdbcTemplate.update(
            "INSERT INTO report_photo (report_id, storage_key, content_type, stage, uploaded_by) "
                + "VALUES (?, 'key/foto2.jpg', 'image/jpeg', 'REPAIR', ?)",
            reportId, f.adminId());

        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM report_photo WHERE report_id = ?", Integer.class, reportId);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("D-50: report_code_seq gera valores sequenciais usados para montar o protocolo OCR-{ano}-{6 digitos}")
    void reportCodeSequenceWorks() {
        Long first = jdbcTemplate.queryForObject("SELECT nextval('report_code_seq')", Long.class);
        Long second = jdbcTemplate.queryForObject("SELECT nextval('report_code_seq')", Long.class);

        assertThat(second).isEqualTo(first + 1);
    }
}
