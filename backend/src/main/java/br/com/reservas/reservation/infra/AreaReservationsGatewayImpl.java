package br.com.reservas.reservation.infra;

import br.com.reservas.area.application.AreaBookingInfo;
import br.com.reservas.area.application.AreaQueryService;
import br.com.reservas.area.application.AreaReservationsGateway;
import br.com.reservas.reservation.domain.Reservation;
import br.com.reservas.reservation.domain.ReservationEvent;
import br.com.reservas.reservation.domain.ReservationStatus;
import br.com.reservas.reservation.infra.ReservationEventRepository;
import br.com.reservas.shared.audit.AuditService;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.shared.reservation.ReservationSummary;
import br.com.reservas.unit.application.UnitService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação real de {@link AreaReservationsGateway} (F4, substitui o
 * antigo {@code NoAreaReservationsGateway}). {@link #findFutureActive(UUID)}
 * já consulta as reservas/bloqueios de verdade (RN-16); {@link #cancelAll}
 * (F4-6, RN-16) cancela cada uma (reserva ou bloqueio) e grava evento +
 * uma auditoria única do lote.
 */
@Component
public class AreaReservationsGatewayImpl implements AreaReservationsGateway {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
        List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservations;
    private final ReservationEventRepository events;
    private final AreaQueryService areaQueryService;
    private final UnitService unitService;
    private final CondominiumLookup condominiumLookup;
    private final AuditService audit;
    private final Clock clock;

    public AreaReservationsGatewayImpl(ReservationRepository reservations, ReservationEventRepository events,
        AreaQueryService areaQueryService, UnitService unitService, CondominiumLookup condominiumLookup,
        AuditService audit, Clock clock) {
        this.reservations = reservations;
        this.events = events;
        this.areaQueryService = areaQueryService;
        this.unitService = unitService;
        this.condominiumLookup = condominiumLookup;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public List<ReservationSummary> findFutureActive(UUID areaId) {
        ZoneId zone = ZoneId.of(condominiumLookup.currentTimezone());
        Instant now = Instant.now(clock);
        String areaName = areaQueryService.findBookable(areaId).map(AreaBookingInfo::name).orElse(null);
        List<Reservation> found = findFutureActiveReservations(areaId, now);
        Map<UUID, String> unitIdentifiersById = unitService.identifiersByIds(
            found.stream().map(Reservation::getUnitId).filter(java.util.Objects::nonNull).distinct().toList());
        return found.stream().map(r -> toSummary(r, areaName, zone, unitIdentifiersById)).toList();
    }

    /** RN-16: cancela cada reserva/bloqueio futuro ativo da área (`cancelledBy = ADMIN`, RN-29). */
    @Override
    @Transactional
    public int cancelAll(UUID areaId, String justification, UUID actorId) {
        Instant now = Instant.now(clock);
        List<Reservation> affected = findFutureActiveReservations(areaId, now);
        for (Reservation r : affected) {
            r.cancel("ADMIN", justification, now);
            reservations.save(r);
            events.save(new ReservationEvent(r.getId(), "CANCELLED", justification, actorId, now));
        }
        if (!affected.isEmpty()) {
            audit.record(actorId, "ADMIN", "RESERVATION_BULK_CANCEL", "AREA", areaId,
                Map.of("count", affected.size(), "reservationIds", affected.stream().map(Reservation::getId).toList()),
                justification);
        }
        return affected.size();
    }

    private List<Reservation> findFutureActiveReservations(UUID areaId, Instant now) {
        return reservations.findByAreaIdAndStatusInAndStartAtGreaterThanEqualOrderByStartAtAsc(areaId,
            ACTIVE_STATUSES, now);
    }

    private ReservationSummary toSummary(Reservation r, String areaName, ZoneId zone,
        Map<UUID, String> unitIdentifiersById) {
        // RF-RES-10: bloqueio (`kind = BLOCK`) não tem `unitId`; `Map.of()` (mapa vazio) lança
        // NullPointerException num `get(null)`, então precisa checar antes.
        String unitIdentifier = r.getUnitId() == null ? null : unitIdentifiersById.get(r.getUnitId());
        ZonedDateTime start = r.getStartAt().atZone(zone);
        ZonedDateTime end = r.getEndAt().atZone(zone);
        return new ReservationSummary(r.getId(), r.getCode(), r.getKind().name(), r.getAreaId(), areaName,
            unitIdentifier, r.getResidentNameSnapshot(), start.toLocalDate(), start.toLocalTime(),
            end.toLocalTime(), r.getStatus().name());
    }
}
