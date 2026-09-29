package br.com.reservas.shared.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * RNF-07: uma linha por acao auditavel de ADMIN/SINDICO (reserva, area,
 * unidade, report). Entidade so de escrita/leitura simples: nao ha update nem
 * delete, entao nao precisa de setters alem do construtor.
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "condominium_id", nullable = false)
    private UUID condominiumId;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;

    private String justification;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditLog() {
        // JPA
    }

    public AuditLog(UUID condominiumId, UUID actorId, String actorRole, String action, String entityType,
        UUID entityId, Map<String, Object> details, String justification, Instant occurredAt) {
        this.condominiumId = condominiumId;
        this.actorId = actorId;
        this.actorRole = actorRole;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.justification = justification;
        this.occurredAt = occurredAt;
    }

    public UUID id() {
        return id;
    }

    public UUID condominiumId() {
        return condominiumId;
    }

    public UUID actorId() {
        return actorId;
    }

    public String actorRole() {
        return actorRole;
    }

    public String action() {
        return action;
    }

    public String entityType() {
        return entityType;
    }

    public UUID entityId() {
        return entityId;
    }

    public Map<String, Object> details() {
        return details;
    }

    public String justification() {
        return justification;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
