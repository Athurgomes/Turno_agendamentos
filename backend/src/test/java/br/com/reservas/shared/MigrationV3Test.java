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
 * Valida o resultado da migration V3 direto no PostgreSQL real: constraints
 * de unit e resident (RN-02, RN-07, RN-08, RN-10) e a FK de
 * user_account.unit_id que so pode existir a partir desta migration.
 */
@SpringBootTest
@Transactional
class MigrationV3Test extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID condominiumId() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO condominium (id, name) VALUES (?, ?)", id, "Condominio Ficticio Teste");
        return id;
    }

    private UUID insertUnit(UUID condominiumId, String identifier) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO unit (id, condominium_id, number, identifier) VALUES (?, ?, ?, ?)",
            id, condominiumId, identifier, identifier);
        return id;
    }

    private void insertResident(UUID unitId, String name, String cpf, boolean primary) {
        jdbcTemplate.update(
            "INSERT INTO resident (unit_id, name, phone, email, cpf, is_primary) VALUES (?, ?, ?, ?, ?, ?)",
            unitId, name, "5562999998888", primary ? "morador@exemplo.test" : null, cpf, primary);
    }

    @Test
    @DisplayName("RN-02: identifier duplicado (case-insensitive) no mesmo condominio viola a unicidade de unit")
    void duplicateIdentifierInSameCondominiumFails() {
        UUID condominiumId = condominiumId();
        insertUnit(condominiumId, "101a");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO unit (condominium_id, number, identifier) VALUES (?, ?, ?)",
                condominiumId, "101a", "101A"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-07: segundo morador principal ativo na mesma unidade viola a unicidade parcial de resident")
    void secondActivePrimaryResidentInSameUnitFails() {
        UUID unitId = insertUnit(condominiumId(), "201a");
        insertResident(unitId, "Morador Principal", "11144477735", true);

        assertThatThrownBy(() -> insertResident(unitId, "Outro Principal", "22233344405", true))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-07: morador principal sem CPF viola o check de resident")
    void primaryResidentWithoutCpfFails() {
        UUID unitId = insertUnit(condominiumId(), "202a");

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO resident (unit_id, name, phone, email, is_primary) VALUES (?, ?, ?, ?, true)",
                unitId, "Morador Sem Cpf", "5562999998888", "morador@exemplo.test"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-08: CPF repetido entre moradores ativos da mesma unidade viola a unicidade parcial de resident")
    void duplicateCpfInSameUnitFails() {
        UUID unitId = insertUnit(condominiumId(), "203a");
        insertResident(unitId, "Morador Principal", "11144477735", true);

        assertThatThrownBy(() -> insertResident(unitId, "Morador Adicional", "11144477735", false))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("RN-08: mesmo CPF em unidades diferentes nao viola a unicidade de resident (parcial por unidade)")
    void sameCpfInDifferentUnitsSucceeds() {
        UUID condominiumId = condominiumId();
        UUID unitId1 = insertUnit(condominiumId, "204a");
        UUID unitId2 = insertUnit(condominiumId, "205a");
        insertResident(unitId1, "Morador Um", "11144477735", true);

        insertResident(unitId2, "Morador Dois", "11144477735", true);

        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM resident WHERE cpf = '11144477735'", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("RN-07/RN-10: principal desativado (soft delete) libera a unidade para um novo principal")
    void softDeletedPrimaryResidentAllowsNewPrimary() {
        UUID unitId = insertUnit(condominiumId(), "206a");
        insertResident(unitId, "Morador Antigo", "11144477735", true);
        jdbcTemplate.update("UPDATE resident SET deleted_at = now() WHERE unit_id = ? AND is_primary", unitId);

        insertResident(unitId, "Morador Novo", "22233344405", true);

        Integer activePrimaryCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM resident WHERE unit_id = ? AND is_primary AND deleted_at IS NULL",
            Integer.class, unitId);
        assertThat(activePrimaryCount).isEqualTo(1);
    }

    @Test
    @DisplayName("RN-10 (V2): user_account.unit_id tem FK para unit a partir da V3")
    void userAccountUnitIdHasForeignKey() {
        UUID condominiumId = condominiumId();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO user_account (condominium_id, role, username, unit_id, password_hash) "
                    + "VALUES (?, 'UNIT', ?, ?, ?)",
                condominiumId, "unidade999", UUID.randomUUID(), "hash"))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
