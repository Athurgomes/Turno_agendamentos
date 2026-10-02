package br.com.reservas.area.api;

import br.com.reservas.area.domain.Area;
import br.com.reservas.area.domain.AreaInspection;
import br.com.reservas.area.domain.AreaPhoto;
import br.com.reservas.area.domain.OpeningHours;
import br.com.reservas.auth.application.AccountService;
import br.com.reservas.auth.application.AccountSummary;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.shared.storage.FileStorage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
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

    /**
     * F9-1/RNF-04: nomes de autor de todas as fotos da vitrine buscados numa
     * unica consulta (evita N+1 de {@link #toPhotoDto(AreaPhoto, Role)} por
     * foto, que era o gargalo de `GET /areas/{id}`).
     */
    public AreaDetailDto toDetail(Area area, List<OpeningHours> hours, List<AreaPhoto> showcasePhotos,
        Role currentRole) {
        List<OpeningHoursDto> hoursDto = hours.stream().map(OpeningHoursDto::from).toList();
        Map<UUID, String> uploaderNames = uploaderNamesFor(showcasePhotos, currentRole);
        List<PhotoDto> photosDto = showcasePhotos.stream()
            .map(p -> toPhotoDto(p, uploaderNames.get(p.getUploadedBy())))
            .toList();
        return AreaDetailDto.of(area, hoursDto, photosDto);
    }

    /** D-52: para UNIT, `uploadedBy` vem `null` (minimizacao, RNF-01) — o morador nao precisa saber quem fotografou. */
    public PhotoDto toPhotoDto(AreaPhoto photo, Role currentRole) {
        String uploaderName = currentRole == Role.UNIT ? null : accounts.displayNameFor(photo.getUploadedBy());
        return toPhotoDto(photo, uploaderName);
    }

    private PhotoDto toPhotoDto(AreaPhoto photo, String uploaderName) {
        var url = storage.presignedGetUrl(photo.getStorageKey());
        return PhotoDto.of(photo, url, uploaderName);
    }

    private Map<UUID, String> uploaderNamesFor(List<AreaPhoto> photos, Role currentRole) {
        if (currentRole == Role.UNIT || photos.isEmpty()) {
            return Map.of();
        }
        var uploaderIds = photos.stream().map(AreaPhoto::getUploadedBy).collect(Collectors.toSet());
        return accounts.summaries(uploaderIds).values().stream()
            .collect(Collectors.toMap(AccountSummary::id, AreaDtoAssembler::displayName));
    }

    private static String displayName(AccountSummary account) {
        return account.role() == Role.ADMIN ? "Administração" : account.displayName();
    }

    public InspectionDto toInspectionDto(AreaInspection inspection, List<AreaPhoto> photos, Role currentRole) {
        String authorName = accounts.displayNameFor(inspection.getAuthorId());
        List<PhotoDto> photosDto = photos.stream().map(p -> toPhotoDto(p, currentRole)).toList();
        return new InspectionDto(inspection.getId(), inspection.getInspectedAt(),
            inspection.getOverallCondition().name(), inspection.getNotes(),
            new UploadedByDto(inspection.getAuthorId(), authorName), photosDto);
    }
}
