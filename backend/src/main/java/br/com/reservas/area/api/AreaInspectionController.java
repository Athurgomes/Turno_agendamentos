package br.com.reservas.area.api;

import br.com.reservas.area.application.AreaInspectionService;
import br.com.reservas.area.application.UploadedPhoto;
import br.com.reservas.area.domain.AreaInspection;
import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.shared.error.BusinessException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** `/areas/{id}/inspections` (docs/03, RF-ARE-07). Acesso S/A. */
@RestController
@RequestMapping("/api/v1/areas/{areaId}/inspections")
@PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
public class AreaInspectionController {

    private final AreaInspectionService inspectionService;
    private final AreaDtoAssembler assembler;
    private final CurrentUserProvider currentUserProvider;

    public AreaInspectionController(AreaInspectionService inspectionService, AreaDtoAssembler assembler,
        CurrentUserProvider currentUserProvider) {
        this.inspectionService = inspectionService;
        this.assembler = assembler;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<InspectionDto> list(@PathVariable UUID areaId) {
        var role = currentUserProvider.current().role();
        return inspectionService.list(areaId).stream()
            .map(inspection -> assembler.toInspectionDto(inspection,
                inspectionService.photosOf(areaId, inspection.getId()), role))
            .toList();
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionDto create(@PathVariable UUID areaId, @Valid @RequestPart("data") CreateInspectionRequest data,
        @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        CurrentUser user = currentUserProvider.current();
        List<UploadedPhoto> files = toUploadedPhotos(photos);

        AreaInspection inspection = inspectionService.create(areaId, user.id(), user.role().name(),
            data.inspectedAt(), data.overallCondition(), data.notes(), files);
        return assembler.toInspectionDto(inspection, inspectionService.photosOf(areaId, inspection.getId()),
            user.role());
    }

    private static List<UploadedPhoto> toUploadedPhotos(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().map(AreaInspectionController::readBytes).toList();
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
