package br.com.reservas.area.api;

import br.com.reservas.area.application.AreaPhotoService;
import br.com.reservas.area.application.UploadedPhoto;
import br.com.reservas.area.domain.AreaPhoto;
import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.shared.error.BusinessException;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** `/areas/{id}/photos` (docs/03, RF-ARE-06, RN-17): histórico de conservação. Acesso S/A. */
@RestController
@RequestMapping("/api/v1/areas/{areaId}/photos")
@PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
public class AreaPhotoController {

    private final AreaPhotoService photoService;
    private final AreaDtoAssembler assembler;
    private final CurrentUserProvider currentUserProvider;
    private final CondominiumLookup condominiumLookup;
    private final Clock clock;

    public AreaPhotoController(AreaPhotoService photoService, AreaDtoAssembler assembler,
        CurrentUserProvider currentUserProvider, CondominiumLookup condominiumLookup, Clock clock) {
        this.photoService = photoService;
        this.assembler = assembler;
        this.currentUserProvider = currentUserProvider;
        this.condominiumLookup = condominiumLookup;
        this.clock = clock;
    }

    @GetMapping
    public List<PhotoDto> history(@PathVariable UUID areaId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
        @RequestParam(defaultValue = "false") boolean includeArchived) {
        var role = currentUserProvider.current().role();
        return photoService.listHistory(areaId, from, to, includeArchived).stream()
            .map(photo -> assembler.toPhotoDto(photo, role))
            .toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public List<PhotoDto> upload(@PathVariable UUID areaId, @RequestPart("photos") List<MultipartFile> photos,
        @RequestParam(required = false) String caption,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate takenAt,
        @RequestParam(required = false) UUID inspectionId) {
        CurrentUser user = currentUserProvider.current();
        LocalDate effectiveTakenAt = takenAt != null ? takenAt
            : LocalDate.now(clock.withZone(ZoneId.of(condominiumLookup.currentTimezone())));

        List<AreaPhoto> saved = photoService.upload(areaId, user.id(), user.role().name(), toUploadedPhotos(photos),
            caption, effectiveTakenAt, inspectionId, false, 1);
        return saved.stream().map(photo -> assembler.toPhotoDto(photo, user.role())).toList();
    }

    @PatchMapping("/{photoId}")
    public PhotoDto update(@PathVariable UUID areaId, @PathVariable UUID photoId,
        @RequestBody UpdatePhotoRequest request) {
        CurrentUser user = currentUserProvider.current();
        AreaPhoto photo = photoService.update(areaId, photoId, user.id(), user.role().name(), request.caption(),
            request.featured());
        return assembler.toPhotoDto(photo, user.role());
    }

    @PostMapping("/{photoId}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public PhotoDto archive(@PathVariable UUID areaId, @PathVariable UUID photoId) {
        CurrentUser user = currentUserProvider.current();
        AreaPhoto photo = photoService.archive(areaId, photoId, user.id());
        return assembler.toPhotoDto(photo, user.role());
    }

    private static List<UploadedPhoto> toUploadedPhotos(List<MultipartFile> files) {
        return files.stream().map(AreaPhotoController::readBytes).toList();
    }

    private static UploadedPhoto readBytes(MultipartFile file) {
        try {
            return new UploadedPhoto(file.getBytes());
        } catch (IOException e) {
            throw new BusinessException("INVALID_FILE", HttpStatus.UNPROCESSABLE_ENTITY,
                "Não foi possível ler o arquivo enviado.");
        }
    }
}
