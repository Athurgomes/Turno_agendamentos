package br.com.reservas.reservation.infra;

import br.com.reservas.area.application.AreaBookingInfo;
import br.com.reservas.area.application.AreaQueryService;
import br.com.reservas.reservation.domain.Reservation;
import br.com.reservas.reservation.domain.ReservationStatus;
import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.shared.reservation.ReservationSummary;
import br.com.reservas.unit.application.FutureReservationsPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Implementação real de {@link FutureReservationsPort} (F4, substitui o
 * antigo adaptador vazio). Não depende de {@code UnitService} (o chamador já
 * informa {@code unitIdentifier}), então não há ciclo de beans Spring a
 * resolver aqui.
 */
@Component
public class FutureReservationsPortImpl implements FutureReservationsPort {

    private static final List<ReservationStatus> ACTIVE_STATUSES =
        List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    private final ReservationRepository reservations;
    private final AreaQueryService areaQueryService;
    private final CondominiumLookup condominiumLookup;
    private final Clock clock;

    public FutureReservationsPortImpl(ReservationRepository reservations, AreaQueryService areaQueryService,
        CondominiumLookup condominiumLookup, Clock clock) {
        this.reservations = reservations;
        this.areaQueryService = areaQueryService;
        this.condominiumLookup = condominiumLookup;
        this.clock = clock;
    }

    @Override
    public List<ReservationSummary> upcomingActiveByUnit(UUID unitId, String unitIdentifier) {
        ZoneId zone = ZoneId.of(condominiumLookup.currentTimezone());
        Instant now = Instant.now(clock);
        List<Reservation> found = reservations.findByUnitIdAndStatusInAndStartAtGreaterThanEqualOrderByStartAtAsc(
            unitId, ACTIVE_STATUSES, now);
        Map<UUID, AreaBookingInfo> areasById = areaQueryService.findBookableByIds(
            found.stream().map(Reservation::getAreaId).distinct().toList());
        return found.stream().map(r -> toSummary(r, unitIdentifier, zone, areasById)).toList();
    }

    private ReservationSummary toSummary(Reservation r, String unitIdentifier, ZoneId zone,
        Map<UUID, AreaBookingInfo> areasById) {
        String areaName = areasById.containsKey(r.getAreaId()) ? areasById.get(r.getAreaId()).name() : null;
        ZonedDateTime start = r.getStartAt().atZone(zone);
        ZonedDateTime end = r.getEndAt().atZone(zone);
        return new ReservationSummary(r.getId(), r.getCode(), r.getKind().name(), r.getAreaId(), areaName,
            unitIdentifier, r.getResidentNameSnapshot(), start.toLocalDate(), start.toLocalTime(),
            end.toLocalTime(), r.getStatus().name());
    }
}
