package br.com.reservas.report.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Foto de um report (`report_photo`, V7, RN-35): nasce `REPORTED` (junto com
 * o report) ou `REPAIR` (`POST /reports/{id}/photos`, D-55). Nunca é
 * sobrescrita: cada upload grava uma {@code storageKey} nova (RN-17).
 */
@Entity
@Table(name = "report_photo")
public class ReportPhoto {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "report_id", nullable = false)
    private UUID reportId;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReportPhotoStage stage;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReportPhoto() {
        // JPA
    }

    public ReportPhoto(UUID reportId, String storageKey, String contentType, ReportPhotoStage stage,
        UUID uploadedBy, Instant createdAt) {
        this.reportId = reportId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.stage = stage;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getReportId() {
        return reportId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public ReportPhotoStage getStage() {
        return stage;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
