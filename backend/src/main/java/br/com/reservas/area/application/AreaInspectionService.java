package br.com.reservas.area.application;

import br.com.reservas.area.domain.AreaInspection;
import br.com.reservas.area.domain.AreaPhoto;
import br.com.reservas.area.domain.InspectionCondition;
import br.com.reservas.area.infra.AreaInspectionRepository;
import br.com.reservas.shared.audit.AuditService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF-ARE-07: vistoria de conservacao da area (`GET/POST /areas/{id}/inspections`, S/A). */
@Service
public class AreaInspectionService {

    private final AreaInspectionRepository inspections;
    private final AreaPhotoService photos;
    private final AuditService audit;
    private final Clock clock;

    public AreaInspectionService(AreaInspectionRepository inspections, AreaPhotoService photos, AuditService audit,
        Clock clock) {
        this.inspections = inspections;
        this.photos = photos;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AreaInspection> list(UUID areaId) {
        return inspections.findByAreaIdOrderByInspectedAtDesc(areaId);
    }

    @Transactional
    public AreaInspection create(UUID areaId, UUID actorId, String actorRole, LocalDate inspectedAt,
        InspectionCondition condition, String notes, List<UploadedPhoto> files) {
        AreaInspection inspection = inspections.save(
            new AreaInspection(areaId, inspectedAt, condition, notes, actorId, Instant.now(clock)));
        // RF-ARE-07: fotos da vistoria recebem inspection_id e taken_at = inspectedAt; nao entram na vitrine.
        if (!files.isEmpty()) {
            photos.upload(areaId, actorId, actorRole, files, null, inspectedAt, inspection.getId(), false, 0);
        }
        audit.record(actorId, actorRole, "AREA_INSPECTION_CREATE", "AREA", areaId,
            Map.of("inspectionId", inspection.getId(), "overallCondition", condition), null);
        return inspection;
    }

    @Transactional(readOnly = true)
    public List<AreaPhoto> photosOf(UUID areaId, UUID inspectionId) {
        return photos.listHistory(areaId, null, null, true).stream()
            .filter(p -> inspectionId.equals(p.getInspectionId()))
            .toList();
    }

    /**
     * `GET /dashboard/home` (F7-1, RF-SIN-01): data da última vistoria de cada
     * área (ausente na lista = nunca vistoriada), em uma única consulta.
     */
    @Transactional(readOnly = true)
    public Map<UUID, LocalDate> lastInspectionByAreaIds(Collection<UUID> areaIds) {
        if (areaIds.isEmpty()) {
            return Map.of();
        }
        return inspections.findLastInspectedAtByAreaIdIn(areaIds).stream()
            .collect(Collectors.toMap(AreaInspectionRepository.LastInspectionProjection::getAreaId,
                AreaInspectionRepository.LastInspectionProjection::getLastInspectedAt));
    }
}
