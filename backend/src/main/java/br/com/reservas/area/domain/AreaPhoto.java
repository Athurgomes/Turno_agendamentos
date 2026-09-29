package br.com.reservas.area.domain;

import br.com.reservas.shared.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Foto do histórico de conservação da área (`area_photo`, V5, RN-17). Nunca é
 * sobrescrita: cada upload grava uma {@code storage_key} nova. `featured`
 * (vitrine) e `archived` são mutuamente exclusivos.
 */
@Entity
@Table(name = "area_photo")
public class AreaPhoto {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "inspection_id")
    private UUID inspectionId;

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    private String caption;

    @Column(nullable = false)
    private boolean featured;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "taken_at", nullable = false)
    private LocalDate takenAt;

    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AreaPhoto() {
        // JPA
    }

    public AreaPhoto(UUID areaId, UUID inspectionId, String storageKey, String contentType, String caption,
        boolean featured, LocalDate takenAt, UUID uploadedBy, Instant createdAt) {
        this.areaId = areaId;
        this.inspectionId = inspectionId;
        this.storageKey = storageKey;
        this.contentType = contentType;
        this.caption = caption;
        this.featured = featured;
        this.takenAt = takenAt;
        this.uploadedBy = uploadedBy;
        this.createdAt = createdAt;
    }

    /** PATCH: legenda/vitrine. Foto arquivada nunca volta pra vitrine. */
    public void update(String caption, Boolean featured) {
        if (caption != null) {
            this.caption = caption;
        }
        if (featured != null) {
            if (featured && archived) {
                throw new BusinessException("INVALID_STATUS_TRANSITION", HttpStatus.CONFLICT,
                    "Uma foto arquivada não pode voltar para a vitrine.");
            }
            this.featured = featured;
        }
    }

    /** ADMIN: some da vitrine, permanece no histórico interno. */
    public void archive() {
        this.archived = true;
        this.featured = false;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public UUID getInspectionId() {
        return inspectionId;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getContentType() {
        return contentType;
    }

    public String getCaption() {
        return caption;
    }

    public boolean isFeatured() {
        return featured;
    }

    public boolean isArchived() {
        return archived;
    }

    public LocalDate getTakenAt() {
        return takenAt;
    }

    public UUID getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
