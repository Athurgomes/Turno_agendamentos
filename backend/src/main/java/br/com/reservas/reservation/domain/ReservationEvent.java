package br.com.reservas.reservation.domain;

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
 * Histórico da reserva (`reservation_event`, V6, RF-RES-09): `CREATED` (F4-2),
 * `UPDATED`/`CANCELLED` (F4-5); `PAYMENT_CONFIRMED` (confirmar pagamento) e `EXPIRED`
 * (RN-31, ator sempre `null`) são da F5.
 * `changes` guarda o antes/depois de `UPDATED` (docs/03 `GET
 * /reservations/{id}/events`); `null` nos demais tipos.
 */
@Entity
@Table(name = "reservation_event")
public class ReservationEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(nullable = false)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> changes;

    private String justification;

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected ReservationEvent() {
        // JPA
    }

    public ReservationEvent(UUID reservationId, String type, String justification, UUID actorId,
        Instant occurredAt) {
        this(reservationId, type, justification, actorId, occurredAt, null);
    }

    public ReservationEvent(UUID reservationId, String type, String justification, UUID actorId, Instant occurredAt,
        Map<String, Object> changes) {
        this.reservationId = reservationId;
        this.type = type;
        this.justification = justification;
        this.actorId = actorId;
        this.occurredAt = occurredAt;
        this.changes = changes;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReservationId() {
        return reservationId;
    }

    public String getType() {
        return type;
    }

    public String getJustification() {
        return justification;
    }

    public Map<String, Object> getChanges() {
        return changes;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
