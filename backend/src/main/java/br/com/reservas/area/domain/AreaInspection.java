package br.com.reservas.area.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Vistoria de conservacao (`area_inspection`, V5, RF-ARE-07). */
@Entity
@Table(name = "area_inspection")
public class AreaInspection {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "inspected_at", nullable = false)
    private LocalDate inspectedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_condition", nullable = false)
    private InspectionCondition overallCondition;

    private String notes;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AreaInspection() {
        // JPA
    }

    public AreaInspection(UUID areaId, LocalDate inspectedAt, InspectionCondition overallCondition, String notes,
        UUID authorId, Instant createdAt) {
        this.areaId = areaId;
        this.inspectedAt = inspectedAt;
        this.overallCondition = overallCondition;
        this.notes = notes;
        this.authorId = authorId;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public LocalDate getInspectedAt() {
        return inspectedAt;
    }

    public InspectionCondition getOverallCondition() {
        return overallCondition;
    }

    public String getNotes() {
        return notes;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
