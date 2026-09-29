package br.com.reservas.shared;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Valida o resultado da migration V5 direto no PostgreSQL real: constraints
 * de common_area (RN-11, RN-12, RN-13, RN-14), area_opening_hours (D-44) e
 * area_photo (RN-17).
 */
@SpringBootTest
@Transactional
class MigrationV5Test extends AbstractIntegrationTest {

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

    private UUID insertArea(UUID condominiumId, int capacity, boolean requiresPayment) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO common_area (id, condominium_id, name, category, description, rules, "
                + "conduct_guidelines, capacity, requires_payment, price, payment_whatsapp) "
                + "VALUES (?, ?, ?, 'PARTY_ROOM', 'desc', 'rules', 'conduct', ?, ?, ?, ?)",
            id, condominiumId, "Salao de Festas", capacity, requiresPayment,
            requiresPayment ? new java.math.BigDecimal("50.00") : null,
            requiresPayment ? "5562999998888" : null);
        return id;
    }

    @Test
    @DisplayName("RN-12: area paga sem preco/whatsapp viola o check de common_area")
    void payingAreaWithoutPriceOrWhatsappFails() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO common_area (condominium_id, name, category, description, rules, "
                    + "conduct_guidelines, capacity, requires_payment) "
                    + "VALUES (?, 'Quadra', 'SPORTS_COURT', 'desc', 'rules', 'conduct', 10, true)",
                condominiumId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-11: capacidade zero viola o check de common_area")
    void zeroCapacityFails() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> insertArea(condominiumId, 0, false))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-13: categoria invalida viola o check de common_area")
    void invalidCategoryFails() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO common_area (condominium_id, name, category, description, rules, "
                    + "conduct_guidelines, capacity) VALUES (?, 'Area', 'INVALID', 'd', 'r', 'c', 10)",
                condominiumId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-14: status invalido viola o check de common_area")
    void invalidStatusFails() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO common_area (condominium_id, name, category, description, rules, "
                    + "conduct_guidelines, capacity, status) "
                    + "VALUES (?, 'Area', 'PARTY_ROOM', 'd', 'r', 'c', 10, 'INVALID')",
                condominiumId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("D-44: horario com minutos fora de {0,30} viola o check de area_opening_hours")
    void openingHoursNotOnHalfHourStepFails() {
        UUID areaId = insertArea(condominiumId(), 10, false);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO area_opening_hours (area_id, day_of_week, open_time, close_time) "
                    + "VALUES (?, 1, '10:15', '18:00')",
                areaId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("D-44: fechamento igual ou anterior a abertura viola o check de area_opening_hours")
    void closeTimeNotAfterOpenTimeFails() {
        UUID areaId = insertArea(condominiumId(), 10, false);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO area_opening_hours (area_id, day_of_week, open_time, close_time) "
                    + "VALUES (?, 1, '18:00', '18:00')",
                areaId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("D-44: dois horarios para o mesmo dia da semana violam a unicidade de area_opening_hours")
    void duplicateDayOfWeekFails() {
        UUID areaId = insertArea(condominiumId(), 10, false);
        jdbcTemplate.update(
            "INSERT INTO area_opening_hours (area_id, day_of_week, open_time, close_time) "
                + "VALUES (?, 1, '08:00', '18:00')",
            areaId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO area_opening_hours (area_id, day_of_week, open_time, close_time) "
                    + "VALUES (?, 1, '09:00', '20:00')",
                areaId))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-17: foto marcada como featured e archived ao mesmo tempo viola o check de area_photo")
    void featuredAndArchivedPhotoFails() {
        UUID condominiumId = condominiumId();
        UUID areaId = insertArea(condominiumId, 10, false);
        UUID uploadedBy = adminId(condominiumId);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO area_photo (area_id, storage_key, content_type, featured, archived, "
                    + "taken_at, uploaded_by) VALUES (?, 'areas/1.jpg', 'image/jpeg', true, true, "
                    + "current_date, ?)",
                areaId, uploadedBy))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-17: storage_key duplicado viola a unicidade de area_photo (fotos nunca sao sobrescritas)")
    void duplicateStorageKeyFails() {
        UUID condominiumId = condominiumId();
        UUID areaId = insertArea(condominiumId, 10, false);
        UUID uploadedBy = adminId(condominiumId);
        jdbcTemplate.update(
            "INSERT INTO area_photo (area_id, storage_key, content_type, taken_at, uploaded_by) "
                + "VALUES (?, 'areas/dup.jpg', 'image/jpeg', current_date, ?)",
            areaId, uploadedBy);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO area_photo (area_id, storage_key, content_type, taken_at, uploaded_by) "
                    + "VALUES (?, 'areas/dup.jpg', 'image/png', current_date, ?)",
                areaId, uploadedBy))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
