package br.com.reservas.reservation.domain;

import br.com.reservas.shared.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Reserva ou bloqueio (`reservation`, V6). RN-24 (sem sobreposição) é
 * garantida pela exclusion constraint do banco, não aqui. O construtor cria
 * `kind = BOOKING` (F4-2/3, RF-RES-01..04); {@link #block} cria `kind = BLOCK`
 * (F4-6, RF-RES-10). {@link #cancel} é reaproveitado por ambos os `kind`
 * (F4-6/D-49: `cancelledBy = ADMIN` também quando o síndico remove).
 */
@Entity
@Table(name = "reservation")
public class Reservation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "condominium_id", nullable = false)
    private UUID condominiumId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationKind kind;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "resident_id")
    private UUID residentId;

    @Column(name = "resident_name_snapshot")
    private String residentNameSnapshot;

    @Column(name = "resident_phone_snapshot")
    private String residentPhoneSnapshot;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    private Integer guests;

    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "requires_payment_snapshot", nullable = false)
    private boolean requiresPaymentSnapshot;

    @Column(name = "price_snapshot")
    private BigDecimal priceSnapshot;

    @Column(name = "payment_confirmed_at")
    private Instant paymentConfirmedAt;

    @Column(name = "payment_confirmed_by")
    private UUID paymentConfirmedBy;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Reservation() {
        // JPA
    }

    /** RF-RES-01..03/RN-25: cria uma reserva de morador (`kind = BOOKING`) já com os snapshots. */
    public Reservation(String code, UUID condominiumId, UUID areaId, UUID unitId, UUID residentId,
        String residentNameSnapshot, String residentPhoneSnapshot, Instant startAt, Instant endAt, int guests,
        String notes, ReservationStatus status, boolean requiresPaymentSnapshot, BigDecimal priceSnapshot,
        UUID createdBy, Instant createdAt) {
        this.code = code;
        this.condominiumId = condominiumId;
        this.areaId = areaId;
        this.kind = ReservationKind.BOOKING;
        this.unitId = unitId;
        this.residentId = residentId;
        this.residentNameSnapshot = residentNameSnapshot;
        this.residentPhoneSnapshot = residentPhoneSnapshot;
        this.startAt = startAt;
        this.endAt = endAt;
        this.guests = guests;
        this.notes = notes;
        this.status = status;
        this.requiresPaymentSnapshot = requiresPaymentSnapshot;
        this.priceSnapshot = priceSnapshot;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    /** RF-RES-10/RN-33: cria um bloqueio (`kind = BLOCK`), já `CONFIRMED` — sem morador, sem cobrança. */
    public static Reservation block(String code, UUID condominiumId, UUID areaId, Instant startAt, Instant endAt,
        String reason, UUID createdBy, Instant createdAt) {
        Reservation r = new Reservation();
        r.code = code;
        r.condominiumId = condominiumId;
        r.areaId = areaId;
        r.kind = ReservationKind.BLOCK;
        r.startAt = startAt;
        r.endAt = endAt;
        r.notes = reason;
        r.status = ReservationStatus.CONFIRMED;
        r.requiresPaymentSnapshot = false;
        r.createdBy = createdBy;
        r.createdAt = createdAt;
        return r;
    }

    /**
     * RN-32: única porta de transição de status da reserva — RN-30 (morador) e
     * RF-RES-08 (ADMIN) chamam {@link #cancel}; a confirmação de pagamento da
     * F5 reutiliza {@link #transitionTo} do mesmo jeito, sem duplicar a
     * matriz de transições permitidas.
     */
    private void transitionTo(ReservationStatus target) {
        boolean allowed = switch (status) {
            case PENDING_PAYMENT -> target == ReservationStatus.CONFIRMED || target == ReservationStatus.CANCELLED;
            case CONFIRMED -> target == ReservationStatus.CANCELLED;
            case CANCELLED -> false;
        };
        if (!allowed) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT,
                "Essa reserva não pode mais ser alterada ou cancelada.");
        }
        this.status = target;
    }

    /** RN-30 (morador, dentro do prazo) / RF-RES-08 (ADMIN, com justificativa): RN-29 grava o motivo exibido. */
    public void cancel(String cancelledBy, String reason, Instant when) {
        transitionTo(ReservationStatus.CANCELLED);
        this.cancelledBy = cancelledBy;
        this.statusReason = reason;
        this.cancelledAt = when;
    }

    /**
     * `POST /reservations/{id}/confirm-payment` (ADMIN, RF-PAG-03/RN-32): só de
     * `PENDING_PAYMENT` (inclusive já {@code CANCELLED} por expiração, RN-31,
     * que também vira `409 INVALID_STATUS_TRANSITION` aqui, reaproveitando a
     * mesma matriz de {@link #transitionTo}).
     */
    public void confirmPayment(UUID actorId, Instant when) {
        transitionTo(ReservationStatus.CONFIRMED);
        this.paymentConfirmedAt = when;
        this.paymentConfirmedBy = actorId;
    }

    /**
     * RF-RES-08/RN-27/RN-28: só reserva (`kind = BOOKING`) ativa; RN-25 — não
     * mexe em `requiresPaymentSnapshot`/`priceSnapshot`. A justificativa vira
     * `statusReason` (visível ao morador, RN-29).
     */
    public void reschedule(UUID areaId, Instant startAt, Instant endAt, String justification) {
        if (kind != ReservationKind.BOOKING || status == ReservationStatus.CANCELLED) {
            throw new BusinessException("INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT,
                "Só é possível alterar reservas ativas.");
        }
        this.areaId = areaId;
        this.startAt = startAt;
        this.endAt = endAt;
        this.statusReason = justification;
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public ReservationKind getKind() {
        return kind;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public UUID getResidentId() {
        return residentId;
    }

    public String getResidentNameSnapshot() {
        return residentNameSnapshot;
    }

    public String getResidentPhoneSnapshot() {
        return residentPhoneSnapshot;
    }

    public Instant getStartAt() {
        return startAt;
    }

    public Instant getEndAt() {
        return endAt;
    }

    public Integer getGuests() {
        return guests;
    }

    public String getNotes() {
        return notes;
    }

    public ReservationStatus getStatus() {
        return status;
    }

    /** RN-29: motivo gravado (hoje só preenchido pelo cancelamento, F4-6); `PENDING_PAYMENT`/`CONFIRMED` é derivado. */
    public String getStatusReason() {
        return statusReason;
    }

    public boolean isRequiresPaymentSnapshot() {
        return requiresPaymentSnapshot;
    }

    public BigDecimal getPriceSnapshot() {
        return priceSnapshot;
    }

    public Instant getPaymentConfirmedAt() {
        return paymentConfirmedAt;
    }

    public UUID getPaymentConfirmedBy() {
        return paymentConfirmedBy;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
