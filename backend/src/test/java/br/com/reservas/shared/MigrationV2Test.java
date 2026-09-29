package br.com.reservas.shared;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Valida o resultado da migration V2 direto no PostgreSQL real: constraints
 * de user_account, unicidade de username/email e a FK de audit_log.actor_id
 * que so pode existir a partir desta migration.
 */
@SpringBootTest
@Transactional
class MigrationV2Test extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID condominiumId() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO condominium (id, name) VALUES (?, ?)", id, "Condominio Ficticio Teste");
        return id;
    }

    // user_account.unit_id ganhou FK para unit na V3; testes desta classe que
    // precisam de um unit_id valido usam este helper.
    private UUID insertUnit(UUID condominiumId, String number) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO unit (id, condominium_id, number, identifier) VALUES (?, ?, ?, ?)",
            id, condominiumId, number, number);
        return id;
    }

    @Test
    @DisplayName("RN-02: conta UNIT sem unit_id viola o check da tabela user_account")
    void unitAccountWithoutUnitIdFails() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_account (condominium_id, role, username, password_hash) VALUES (?, 'UNIT', ?, ?)",
                condominiumId, "unidade101", "hash"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-06: conta ADMIN sem e-mail viola o check da tabela user_account")
    void adminAccountWithoutEmailFails() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_account (condominium_id, role, password_hash) VALUES (?, 'ADMIN', ?)",
                condominiumId, "hash"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-06: e-mail duplicado (case-insensitive) viola a unicidade de user_account")
    void duplicateEmailCaseInsensitiveFails() {
        UUID condominiumId = condominiumId();
        jdbcTemplate.update(
            "INSERT INTO user_account (condominium_id, role, email, password_hash) VALUES (?, 'SYNDIC', ?, ?)",
            condominiumId, "sindico@exemplo.test", "hash");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_account (condominium_id, role, email, password_hash) VALUES (?, 'SYNDIC', ?, ?)",
                condominiumId, "SINDICO@exemplo.test", "hash"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-02: username duplicado (case-insensitive) no mesmo condominio viola a unicidade de user_account")
    void duplicateUsernameInSameCondominiumFails() {
        UUID condominiumId = condominiumId();
        UUID unitId1 = insertUnit(condominiumId, "101");
        UUID unitId2 = insertUnit(condominiumId, "102");
        jdbcTemplate.update(
            "INSERT INTO user_account (condominium_id, role, username, unit_id, password_hash) VALUES (?, 'UNIT', ?, ?, ?)",
            condominiumId, "unidade101", unitId1, "hash");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_account (condominium_id, role, username, unit_id, password_hash) VALUES (?, 'UNIT', ?, ?, ?)",
                condominiumId, "UNIDADE101", unitId2, "hash"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RNF-07: audit_log.actor_id tem FK para user_account a partir da V2")
    void auditLogActorIdHasForeignKey() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO audit_log (condominium_id, actor_id, action, entity_type, occurred_at) "
                    + "VALUES (?, ?, 'CREATE', 'RESERVATION', now())",
                condominiumId, UUID.randomUUID()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
