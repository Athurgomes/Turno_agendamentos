package br.com.reservas.area.api;

import br.com.reservas.area.domain.AreaPhoto;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** `PhotoDto` (docs/03, D-41): `url` e pre-assinada, gerada depois da checagem de acesso do chamador. */
public record PhotoDto(UUID id, String url, String caption, boolean featured, boolean archived, LocalDate takenAt,
    Instant createdAt, UploadedByDto uploadedBy, UUID inspectionId) {

    /** `uploaderName == null` (D-52, UNIT) -&gt; `uploadedBy` sai `null` no JSON, nao `{ id, name: null }`. */
    public static PhotoDto of(AreaPhoto photo, URI presignedUrl, String uploaderName) {
        UploadedByDto uploadedBy = uploaderName != null ? new UploadedByDto(photo.getUploadedBy(), uploaderName)
            : null;
        return new PhotoDto(photo.getId(), presignedUrl.toString(), photo.getCaption(), photo.isFeatured(),
            photo.isArchived(), photo.getTakenAt(), photo.getCreatedAt(), uploadedBy, photo.getInspectionId());
    }
}
