package br.com.reservas.shared.audit;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.reservas.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** RNF-07: toda acao de ADMIN/SINDICO que altera reserva/area/unidade/report vira uma linha em `audit_log`. */
@SpringBootTest
class AuditServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("RNF-07: AuditService.record grava condominium_id, actor, action e details (jsonb) em audit_log")
    void recordsAuditLogWithJsonbDetails() {
        UUID condominiumId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO condominium (id, name) VALUES (?, ?)", condominiumId, "Condominio Auditoria");
        UUID actorId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO user_account (id, condominium_id, role, email, password_hash) VALUES (?, ?, 'ADMIN', ?, ?)",
            actorId, condominiumId, "admin.auditoria@exemplo.test", "hash");
        UUID entityId = UUID.randomUUID();

        auditService.record(actorId, "ADMIN", "AREA_STATUS_CHANGED", "AREA", entityId,
            Map.of("from", "ACTIVE", "to", "MAINTENANCE"), "Reforma da piscina");

        var row = jdbcTemplate.queryForMap(
            "SELECT condominium_id, actor_id, actor_role, action, entity_type, entity_id, justification, "
                + "details ->> 'from' AS details_from, details ->> 'to' AS details_to "
                + "FROM audit_log WHERE entity_id = ?", entityId);

        assertThat(row.get("condominium_id")).isEqualTo(condominiumId);
        assertThat(row.get("actor_id")).isEqualTo(actorId);
        assertThat(row.get("actor_role")).isEqualTo("ADMIN");
        assertThat(row.get("action")).isEqualTo("AREA_STATUS_CHANGED");
        assertThat(row.get("entity_type")).isEqualTo("AREA");
        assertThat(row.get("justification")).isEqualTo("Reforma da piscina");
        assertThat(row.get("details_from")).isEqualTo("ACTIVE");
        assertThat(row.get("details_to")).isEqualTo("MAINTENANCE");
    }
}
