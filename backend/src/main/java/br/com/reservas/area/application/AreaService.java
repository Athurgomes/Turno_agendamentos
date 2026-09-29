package br.com.reservas.area.application;

import br.com.reservas.area.domain.Area;
import br.com.reservas.area.domain.AreaCategory;
import br.com.reservas.area.domain.AreaStatus;
import br.com.reservas.area.domain.OpeningHours;
import br.com.reservas.area.infra.AreaRepository;
import br.com.reservas.area.infra.OpeningHoursRepository;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.shared.audit.AuditService;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.shared.reservation.ReservationSummary;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `/areas` (RF-ARE-01..05, 09; RN-11..16; D-20, D-44): cadastro, edição com
 * permissão por campo (SYNDIC x ADMIN), mudança de status e exclusão com
 * detecção de reservas afetadas.
 */
@Service
public class AreaService {

    private final AreaRepository areas;
    private final OpeningHoursRepository openingHours;
    private final AreaPhotoService photos;
    private final AreaReservationsGateway reservationsGateway;
    private final AuditService audit;
    private final Clock clock;

    public AreaService(AreaRepository areas, OpeningHoursRepository openingHours, AreaPhotoService photos,
        AreaReservationsGateway reservationsGateway, AuditService audit, Clock clock) {
        this.areas = areas;
        this.openingHours = openingHours;
        this.photos = photos;
        this.reservationsGateway = reservationsGateway;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Area findActiveOrThrow(UUID id) {
        return areas.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new EntityNotFoundException("Área não encontrada: " + id));
    }

    @Transactional(readOnly = true)
    public List<Area> catalog(UUID condominiumId, AreaCategory category, AreaStatus status) {
        if (category != null && status != null) {
            return areas.findByCondominiumIdAndDeletedAtIsNullAndCategoryAndStatus(condominiumId, category, status);
        }
        if (category != null) {
            return areas.findByCondominiumIdAndDeletedAtIsNullAndCategory(condominiumId, category);
        }
        if (status != null) {
            return areas.findByCondominiumIdAndDeletedAtIsNullAndStatus(condominiumId, status);
        }
        return areas.findByCondominiumIdAndDeletedAtIsNull(condominiumId);
    }

    @Transactional(readOnly = true)
    public List<OpeningHours> openingHoursOf(UUID areaId) {
        return openingHours.findByAreaIdOrderByDayOfWeek(areaId);
    }

    /** RN-11/RN-12: cria a área, o horário de funcionamento e as fotos iniciais (vitrine). */
    @Transactional
    public Area create(UUID condominiumId, UUID actorId, CreateAreaCommand command, List<UploadedPhoto> files,
        LocalDate today) {
        if (command.openingHours() == null || command.openingHours().isEmpty()) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "A área precisa de pelo menos um dia de horário de funcionamento.");
        }
        Area area = new Area(condominiumId, command.name(), command.category(), command.description(),
            command.rules(), command.conductGuidelines(), command.capacity(), command.requiresPayment(),
            command.price(), command.paymentWhatsapp());
        area = areas.save(area);
        saveOpeningHours(area.getId(), command.openingHours());
        // RN-11: fotos do cadastro nascem featured=true (vitrine).
        photos.upload(area.getId(), actorId, Role.ADMIN.name(), files, null, today, null, true, 1);

        audit.record(actorId, Role.ADMIN.name(), "AREA_CREATE", "AREA", area.getId(),
            Map.of("name", area.getName()), null);
        return area;
    }

    /** `PUT /areas/{id}` (D-44): parcial; SYNDIC restrito a campos descritivos (D-20). */
    @Transactional
    public Area update(UUID areaId, Role actorRole, UUID actorId, UpdateAreaCommand command) {
        Area area = findActiveOrThrow(areaId);
        if (command.version() != null && command.version() != area.getVersion()) {
            throw new BusinessException("CONFLICT", HttpStatus.CONFLICT,
                "A área foi alterada por outra pessoa; recarregue e tente novamente.");
        }
        if (actorRole == Role.SYNDIC && command.hasAdminOnlyField()) {
            throw new BusinessException("FORBIDDEN", HttpStatus.FORBIDDEN,
                "Síndico só pode editar descrição, regras, sugestões de conduta e capacidade.");
        }

        Map<String, Object> before = snapshot(area);
        area.applyFields(
            command.name() != null ? command.name() : area.getName(),
            command.category() != null ? command.category() : area.getCategory(),
            command.description() != null ? command.description() : area.getDescription(),
            command.rules() != null ? command.rules() : area.getRules(),
            command.conductGuidelines() != null ? command.conductGuidelines() : area.getConductGuidelines(),
            command.capacity() != null ? command.capacity() : area.getCapacity(),
            command.requiresPayment() != null ? command.requiresPayment() : area.isRequiresPayment(),
            command.price() != null ? command.price() : area.getPrice(),
            command.paymentWhatsapp() != null ? command.paymentWhatsapp() : area.getPaymentWhatsapp());
        Area saved = areas.save(area);

        // D-44: alterar horário não mexe em reservas existentes; só ADMIN chega aqui (campo restrito).
        if (command.openingHours() != null) {
            saveOpeningHours(areaId, command.openingHours());
        }

        audit.record(actorId, actorRole.name(), "AREA_UPDATE", "AREA", areaId,
            Map.of("before", before, "after", snapshot(saved)), null);
        return saved;
    }

    /** `PATCH /areas/{id}/status` (ADMIN, RN-14/16). */
    @Transactional
    public AreaStatusResult changeStatus(UUID areaId, UUID actorId, AreaStatus newStatus, String justification,
        boolean confirmCancelAffected) {
        Area area = findActiveOrThrow(areaId);
        int cancelled = 0;
        if (newStatus != AreaStatus.ACTIVE) {
            cancelled = enforceNoFutureReservationsOrCancel(areaId, actorId, justification, confirmCancelAffected);
        }
        area.changeStatus(newStatus);
        Area saved = areas.save(area);

        audit.record(actorId, Role.ADMIN.name(), "AREA_STATUS_CHANGE", "AREA", areaId,
            Map.of("status", newStatus, "cancelledReservations", cancelled), justification);
        return new AreaStatusResult(saved, cancelled);
    }

    /** `DELETE /areas/{id}` (ADMIN, RN-15/16): soft delete. */
    @Transactional
    public void delete(UUID areaId, UUID actorId, String justification, boolean confirmCancelAffected) {
        Area area = findActiveOrThrow(areaId);
        enforceNoFutureReservationsOrCancel(areaId, actorId, justification, confirmCancelAffected);
        area.softDelete(Instant.now(clock));
        areas.save(area);

        audit.record(actorId, Role.ADMIN.name(), "AREA_DELETE", "AREA", areaId, Map.of(), justification);
    }

    /** RN-16: sem confirmação e com reservas futuras ativas -> 409; com confirmação, exige justificativa e cancela. */
    private int enforceNoFutureReservationsOrCancel(UUID areaId, UUID actorId, String justification,
        boolean confirmCancelAffected) {
        List<ReservationSummary> affected = reservationsGateway.findFutureActive(areaId);
        if (affected.isEmpty()) {
            return 0;
        }
        if (!confirmCancelAffected) {
            throw new AreaHasFutureReservationsException(affected);
        }
        if (justification == null || justification.trim().length() < 10) {
            throw new BusinessException("JUSTIFICATION_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY,
                "Justificativa precisa ter pelo menos 10 caracteres.");
        }
        return reservationsGateway.cancelAll(areaId, justification, actorId);
    }

    private void saveOpeningHours(UUID areaId, List<OpeningHoursInput> inputs) {
        openingHours.deleteByAreaId(areaId);
        inputs.stream()
            .map(i -> new OpeningHours(areaId, i.dayOfWeek(), i.openTime(), i.closeTime()))
            .forEach(openingHours::save);
    }

    private static Map<String, Object> snapshot(Area area) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", area.getName());
        map.put("category", area.getCategory());
        map.put("description", area.getDescription());
        map.put("rules", area.getRules());
        map.put("conductGuidelines", area.getConductGuidelines());
        map.put("capacity", area.getCapacity());
        map.put("requiresPayment", area.isRequiresPayment());
        map.put("price", area.getPrice());
        map.put("paymentWhatsapp", area.getPaymentWhatsapp());
        return map;
    }

    public record AreaStatusResult(Area area, int cancelledReservations) {
    }
}
