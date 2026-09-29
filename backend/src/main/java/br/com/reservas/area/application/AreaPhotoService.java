package br.com.reservas.area.application;

import br.com.reservas.shared.audit.AuditService;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.shared.storage.FileStorage;
import br.com.reservas.shared.storage.ImageFileValidator;
import br.com.reservas.area.domain.AreaPhoto;
import br.com.reservas.area.infra.AreaInspectionRepository;
import br.com.reservas.area.infra.AreaPhotoRepository;
import br.com.reservas.auth.domain.Role;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-17/RF-ARE-06/07: histórico imutável de fotos da área. Fotos nunca são
 * sobrescritas (chave `areas/{areaId}/{uuid}.{ext}` sempre nova) e o tipo real
 * do arquivo é checado pelos bytes, não pela extensão/`Content-Type`
 * informados pelo cliente (vibe-security).
 */
@Service
public class AreaPhotoService {

    private final AreaPhotoRepository photos;
    private final AreaInspectionRepository inspections;
    private final FileStorage storage;
    private final AuditService audit;
    private final Clock clock;

    public AreaPhotoService(AreaPhotoRepository photos, AreaInspectionRepository inspections, FileStorage storage,
        AuditService audit, Clock clock) {
        this.photos = photos;
        this.inspections = inspections;
        this.storage = storage;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AreaPhoto> listShowcase(UUID areaId) {
        return photos.findByAreaIdAndFeaturedTrueAndArchivedFalseOrderByTakenAtDesc(areaId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<AreaPhoto> findCover(UUID areaId) {
        return photos.findFirstByAreaIdAndFeaturedTrueAndArchivedFalseOrderByTakenAtDescCreatedAtDesc(areaId);
    }

    /** `GET /areas/{id}/photos` (S/A): historico completo, filtrado por `takenAt`. */
    @Transactional(readOnly = true)
    public List<AreaPhoto> listHistory(UUID areaId, LocalDate from, LocalDate to, boolean includeArchived) {
        LocalDate effectiveFrom = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate effectiveTo = to != null ? to : LocalDate.of(9999, 12, 31);
        return includeArchived
            ? photos.findByAreaIdAndTakenAtBetweenOrderByTakenAtDesc(areaId, effectiveFrom, effectiveTo)
            : photos.findByAreaIdAndArchivedFalseAndTakenAtBetweenOrderByTakenAtDesc(areaId, effectiveFrom,
                effectiveTo);
    }

    /**
     * RN-11 (cadastro), RF-ARE-06 (`POST /areas/{id}/photos`) e RF-ARE-07
     * (fotos de vistoria): valida quantidade e tipo/tamanho de cada arquivo,
     * grava no storage com chave nunca reutilizada e persiste o registro.
     */
    @Transactional
    public List<AreaPhoto> upload(UUID areaId, UUID actorId, String actorRole, List<UploadedPhoto> files,
        String caption, LocalDate takenAt, UUID inspectionId, boolean featured, int minCount) {
        if (files.size() < minCount || files.size() > 10) {
            throw new BusinessException("INVALID_FILE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Envie entre " + minCount + " e 10 fotos.");
        }
        // vibe-security/RF-ARE-07: inspectionId de outra área (ou inexistente) não pode ser
        // aceito; checa ANTES de gravar qualquer byte no storage.
        if (inspectionId != null && !inspections.existsByIdAndAreaId(inspectionId, areaId)) {
            throw new EntityNotFoundException("Vistoria não encontrada: " + inspectionId);
        }
        Instant now = Instant.now(clock);
        List<AreaPhoto> saved = files.stream()
            .map(file -> savePhoto(areaId, actorId, file, caption, takenAt, inspectionId, featured, now))
            .toList();
        audit.record(actorId, actorRole, "AREA_PHOTO_ADD", "AREA", areaId,
            Map.of("count", saved.size(), "inspectionId", String.valueOf(inspectionId)), null);
        return saved;
    }

    private AreaPhoto savePhoto(UUID areaId, UUID actorId, UploadedPhoto file, String caption, LocalDate takenAt,
        UUID inspectionId, boolean featured, Instant now) {
        ImageFileValidator.Validated validated = ImageFileValidator.validate(file.content());
        // RN-17: chave nunca reutilizada, nunca baseada em nome enviado pelo cliente.
        String key = "areas/" + areaId + "/" + UUID.randomUUID() + "." + validated.extension();
        storage.put(key, file.content(), validated.contentType());
        AreaPhoto photo = new AreaPhoto(areaId, inspectionId, key, validated.contentType(), caption, featured,
            takenAt, actorId, now);
        return photos.save(photo);
    }

    /** `PATCH /areas/{id}/photos/{photoId}` (S/A): legenda/vitrine; arquivada nunca volta a vitrine. */
    @Transactional
    public AreaPhoto update(UUID areaId, UUID photoId, UUID actorId, String actorRole, String caption,
        Boolean featured) {
        AreaPhoto photo = findOrThrow(areaId, photoId);
        photo.update(caption, featured);
        AreaPhoto saved = photos.save(photo);
        audit.record(actorId, actorRole, "AREA_PHOTO_UPDATE", "AREA_PHOTO", photoId, Map.of(), null);
        return saved;
    }

    /** `POST /areas/{id}/photos/{photoId}/archive` (ADMIN). */
    @Transactional
    public AreaPhoto archive(UUID areaId, UUID photoId, UUID actorId) {
        AreaPhoto photo = findOrThrow(areaId, photoId);
        photo.archive();
        AreaPhoto saved = photos.save(photo);
        audit.record(actorId, Role.ADMIN.name(), "AREA_PHOTO_ARCHIVE", "AREA_PHOTO", photoId, Map.of(), null);
        return saved;
    }

    private AreaPhoto findOrThrow(UUID areaId, UUID photoId) {
        return photos.findByIdAndAreaId(photoId, areaId)
            .orElseThrow(() -> new EntityNotFoundException("Foto não encontrada: " + photoId));
    }
}
