package br.com.reservas.shared;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.reservas.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * D-12/D-13: valida o resultado da migration V1 direto no PostgreSQL real
 * (extensao btree_gist e defaults de condominium_settings). Nao cria
 * entidades JPA; usa JdbcTemplate e um condominio ficticio com rollback.
 */
@SpringBootTest
class MigrationV1Test extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("D-12: extensao btree_gist esta instalada (pre-requisito da exclusion constraint de reservas)")
    void btreeGistExtensionIsInstalled() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM pg_extension WHERE extname = 'btree_gist'", Integer.class);

        assertThat(count).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("RN-19/RN-20/RN-21/RN-22/RN-30/RN-34: condominium_settings aplica os defaults do motor de regras v0")
    void condominiumSettingsAppliesDefaults() {
        UUID condominiumId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO condominium (id, name) VALUES (?, ?)",
            condominiumId, "Condominio Ficticio Teste");
        jdbcTemplate.update(
            "INSERT INTO condominium_settings (condominium_id) VALUES (?)",
            condominiumId);

        var settings = jdbcTemplate.queryForMap(
            "SELECT * FROM condominium_settings WHERE condominium_id = ?", condominiumId);

        assertThat(settings.get("min_advance_days")).isEqualTo(1);
        assertThat(settings.get("next_day_window_start").toString()).isEqualTo("06:00:00");
        assertThat(settings.get("next_day_window_end").toString()).isEqualTo("16:00:00");
        assertThat(settings.get("max_advance_days")).isEqualTo(60);
        assertThat(settings.get("max_active_bookings_per_unit")).isEqualTo(3);
        assertThat(settings.get("resident_cancel_deadline_hours")).isEqualTo(24);
        assertThat(settings.get("slot_minutes")).isEqualTo(30);
        assertThat(settings.get("report_window_days")).isEqualTo(7);
    }
}
