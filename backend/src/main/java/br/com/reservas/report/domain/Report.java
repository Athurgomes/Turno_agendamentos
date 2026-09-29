package br.com.reservas.report.domain;

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
 * Ocorrência reportada pelo morador sobre uma reserva `CONFIRMED` (`report`,
 * V7, RF-REP-01..05). {@link #changeStatus} é a única porta de transição de
 * status (RN-36): só avança na ordem declarada em {@link ReportStatus}, nunca
 * volta; {@link ReportStatus#DISMISSED} é alcançável de qualquer status não
 * final, com justificativa. `maintenanceCost` (RN-37) é responsabilidade do
 * chamador (`report.application`), que só o grava numa transição para
 * `RESOLVED`.
 */
@Entity
@Table(name = "report")
public class Report {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false)
    private String code;

    @Column(name = "condominium_id", nullable = false)
    private UUID condominiumId;

    @Column(name = "reservation_id", nullable = false)
    private UUID reservationId;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "resident_id", nullable = false)
    private UUID residentId;

    @Column(name = "resident_name_snapshot", nullable = false)
    private String residentNameSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportCategory category;

    @Column(nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportStatus status;

    @Column(name = "status_reason")
    private String statusReason;

    @Column(name = "maintenance_cost")
    private BigDecimal maintenanceCost;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Report() {
        // JPA
    }

    /** RF-REP-01: cria um report `OPEN`, com o snapshot do nome do morador informado. */
    public Report(String code, UUID condominiumId, UUID reservationId, UUID areaId, UUID unitId, UUID residentId,
        String residentNameSnapshot, ReportCategory category, String description, Instant now) {
        this.code = code;
        this.condominiumId = condominiumId;
        this.reservationId = reservationId;
        this.areaId = areaId;
        this.unitId = unitId;
        this.residentId = residentId;
        this.residentNameSnapshot = residentNameSnapshot;
        this.category = category;
        this.description = description;
        this.status = ReportStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * RN-36: única porta de transição de status. Status já final
     * (`RESOLVED`/`DISMISSED`) -&gt; `409 INVALID_STATUS_TRANSITION`, seja qual
     * for o alvo (checado primeiro, antes de qualquer outra regra).
     * `target = DISMISSED` exige `justification` (&gt;= 10 caracteres) a partir
     * de qualquer status não final -&gt; `422 JUSTIFICATION_REQUIRED` senão. As
     * demais transições só avançam na ordem de {@link ReportStatus} (nunca
     * voltam, nunca ficam paradas -&gt; `409` também) e a `justification`, se
     * houver, vira `statusReason`.
     */
    public void changeStatus(ReportStatus target, String justification, Instant now) {
        if (status.isFinal()) {
            throw invalidTransition();
        }
        if (target == ReportStatus.DISMISSED) {
            if (justification == null || justification.trim().length() < 10) {
                throw new BusinessException("JUSTIFICATION_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Justificativa precisa ter pelo menos 10 caracteres.");
            }
            this.status = ReportStatus.DISMISSED;
            this.statusReason = justification;
            this.updatedAt = now;
            return;
        }
        if (target.ordinal() <= status.ordinal()) {
            throw invalidTransition();
        }
        this.status = target;
        if (justification != null && !justification.isBlank()) {
            this.statusReason = justification;
        }
        if (target == ReportStatus.RESOLVED) {
            this.resolvedAt = now;
        }
        this.updatedAt = now;
    }

    /** RN-37: só gravado pelo chamador numa transição para `RESOLVED` (validado antes de chamar). */
    public void setMaintenanceCost(BigDecimal maintenanceCost) {
        this.maintenanceCost = maintenanceCost;
    }

    public void touch(Instant now) {
        this.updatedAt = now;
    }

    private static BusinessException invalidTransition() {
        return new BusinessException("INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT,
            "Esse report não pode mais mudar de status.");
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

    public UUID getReservationId() {
        return reservationId;
    }

    public UUID getAreaId() {
        return areaId;
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

    public ReportCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public BigDecimal getMaintenanceCost() {
        return maintenanceCost;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public int getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
