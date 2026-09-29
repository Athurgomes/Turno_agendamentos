package br.com.reservas.area.api;

import br.com.reservas.area.domain.Area;
import br.com.reservas.area.domain.AreaInspection;
import br.com.reservas.area.domain.AreaPhoto;
import br.com.reservas.area.domain.OpeningHours;
import br.com.reservas.auth.application.AccountService;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.shared.storage.FileStorage;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * D-41: unico lugar que gera URL pre-assinada e resolve nome do autor para os
 * DTOs de area/foto/vistoria, sempre depois da checagem de acesso feita pelo
 * controller (a checagem em si e so `@PreAuthorize`/perfil aqui: nao ha
 * recurso restrito por unidade em `area`).
 */
@Component
public class AreaDtoAssembler {

    private final FileStorage storage;
    private final AccountService accounts;

    public AreaDtoAssembler(FileStorage storage, AccountService accounts) {
        this.storage = storage;
        this.accounts = accounts;
    }

    public AreaSummaryDto toSummary(Area area, Optional<AreaPhoto> cover) {
        String coverUrl = cover.map(photo -> storage.presignedGetUrl(photo.getStorageKey()).toString()).orElse(null);
        return AreaSummaryDto.of(area, coverUrl);
    }

    public AreaDetailDto toDetail(Area area, List<OpeningHours> hours, List<AreaPhoto> showcasePhotos,
        Role currentRole) {
        List<OpeningHoursDto> hoursDto = hours.stream().map(OpeningHoursDto::from).toList();
        List<PhotoDto> photosDto = showcasePhotos.stream().map(p -> toPhotoDto(p, currentRole)).toList();
        return AreaDetailDto.of(area, hoursDto, photosDto);
    }

    /** D-52: para UNIT, `uploadedBy` vem `null` (minimizacao, RNF-01) — o morador nao precisa saber quem fotografou. */
    public PhotoDto toPhotoDto(AreaPhoto photo, Role currentRole) {
        var url = storage.presignedGetUrl(photo.getStorageKey());
        String uploaderName = currentRole == Role.UNIT ? null : accounts.displayNameFor(photo.getUploadedBy());
        return PhotoDto.of(photo, url, uploaderName);
    }

    public InspectionDto toInspectionDto(AreaInspection inspection, List<AreaPhoto> photos, Role currentRole) {
        String authorName = accounts.displayNameFor(inspection.getAuthorId());
        List<PhotoDto> photosDto = photos.stream().map(p -> toPhotoDto(p, currentRole)).toList();
        return new InspectionDto(inspection.getId(), inspection.getInspectedAt(),
            inspection.getOverallCondition().name(), inspection.getNotes(),
            new UploadedByDto(inspection.getAuthorId(), authorName), photosDto);
    }
}
