package br.com.reservas.shared.audit;

import br.com.reservas.shared.condominium.CondominiumLookup;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * RNF-07: interface pública do módulo de auditoria. Um único método, para que
 * ADMIN/SÍNDICO em qualquer outro módulo grave uma linha de {@code audit_log}
 * sem conhecer a entidade JPA. Grava na transação corrente do chamador (não
 * abre transação própria: se o caso de uso fizer rollback, a auditoria também
 * é desfeita, o que é o comportamento correto).
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;
    private final CondominiumLookup condominiums;
    private final Clock clock;

    public AuditService(AuditLogRepository repository, CondominiumLookup condominiums, Clock clock) {
        this.repository = repository;
        this.condominiums = condominiums;
        this.clock = clock;
    }

    public void record(UUID actorId, String actorRole, String action, String entityType, UUID entityId,
        Map<String, Object> details, String justification) {
        UUID condominiumId = condominiums.currentId()
            .orElseThrow(() -> new IllegalStateException(
                "Não há condomínio cadastrado; auditoria exige um condomínio (D-13)."));

        AuditLog log = new AuditLog(condominiumId, actorId, actorRole, action, entityType, entityId,
            details, justification, Instant.now(clock));
        // saveAndFlush: quem chama pode consultar audit_log logo em seguida (ex.: nossos
        // próprios testes com JdbcTemplate na mesma transação) sem esperar o commit.
        repository.saveAndFlush(log);
    }
}
