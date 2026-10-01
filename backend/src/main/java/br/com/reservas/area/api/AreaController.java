package br.com.reservas.area.api;

import br.com.reservas.area.application.AreaPhotoService;
import br.com.reservas.area.application.AreaService;
import br.com.reservas.area.application.UploadedPhoto;
import br.com.reservas.area.domain.Area;
import br.com.reservas.area.domain.AreaCategory;
import br.com.reservas.area.domain.AreaPhoto;
import br.com.reservas.area.domain.AreaStatus;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.shared.error.BusinessException;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** `/areas` (docs/03 "Áreas"). Controller fino: regra em {@link AreaService}. */
@RestController
@RequestMapping("/api/v1/areas")
public class AreaController {

    private final AreaService areaService;
    private final AreaPhotoService photoService;
    private final AreaDtoAssembler assembler;
    private final CurrentUserProvider currentUserProvider;
    private final CondominiumLookup condominiumLookup;
    private final Clock clock;

    public AreaController(AreaService areaService, AreaPhotoService photoService, AreaDtoAssembler assembler,
        CurrentUserProvider currentUserProvider, CondominiumLookup condominiumLookup, Clock clock) {
        this.areaService = areaService;
        this.photoService = photoService;
        this.assembler = assembler;
        this.currentUserProvider = currentUserProvider;
        this.condominiumLookup = condominiumLookup;
        this.clock = clock;
    }

    @GetMapping
    public List<AreaSummaryDto> catalog(@RequestParam(required = false) AreaCategory category,
        @RequestParam(required = false) AreaStatus status) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        List<Area> areasFound = areaService.catalog(condominiumId, category, status);
        // RNF-04: uma unica consulta para a capa de todas as areas (evita N+1).
        var covers = photoService.findCovers(areasFound.stream().map(Area::getId).toList());
        return areasFound.stream()
            .map(area -> assembler.toSummary(area, Optional.ofNullable(covers.get(area.getId()))))
            .toList();
    }

    @GetMapping("/{id}")
    public AreaDetailDto detail(@PathVariable UUID id) {
        return toDetailDto(areaService.findActiveOrThrow(id));
    }

    @PostMapping(consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public AreaDetailDto create(@Valid @RequestPart("data") CreateAreaRequest data,
        @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        UUID actorId = currentUserProvider.current().id();
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(condominiumLookup.currentTimezone())));

        Area area = areaService.create(condominiumId, actorId, data.toCommand(), toUploadedPhotos(photos), today);
        return toDetailDto(area);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
    public AreaDetailDto update(@PathVariable UUID id, @Valid @RequestBody UpdateAreaRequest request) {
        var user = currentUserProvider.current();
        Area area = areaService.update(id, user.role(), user.id(), request.toCommand());
        return toDetailDto(area);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public AreaStatusResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody AreaStatusRequest request) {
        UUID actorId = currentUserProvider.current().id();
        var result = areaService.changeStatus(id, actorId, request.status(), request.justification(),
            request.confirmed());
        return new AreaStatusResponse(toDetailDto(result.area()), result.cancelledReservations());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void delete(@PathVariable UUID id, @RequestBody(required = false) DeleteAreaRequest body) {
        UUID actorId = currentUserProvider.current().id();
        DeleteAreaRequest request = body != null ? body : new DeleteAreaRequest(null, false);
        areaService.delete(id, actorId, request.justification(), request.confirmed());
    }

    private AreaDetailDto toDetailDto(Area area) {
        List<AreaPhoto> showcase = photoService.listShowcase(area.getId());
        var hours = areaService.openingHoursOf(area.getId());
        return assembler.toDetail(area, hours, showcase, currentUserProvider.current().role());
    }

    private static List<UploadedPhoto> toUploadedPhotos(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        return files.stream().map(AreaController::readBytes).toList();
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
