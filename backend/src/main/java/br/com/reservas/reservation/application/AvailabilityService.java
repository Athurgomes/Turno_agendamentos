package br.com.reservas.reservation.application;

import br.com.reservas.area.application.AreaBookingInfo;
import br.com.reservas.area.application.AreaQueryService;
import br.com.reservas.area.application.OpeningHoursRange;
import br.com.reservas.reservation.domain.Reservation;
import br.com.reservas.reservation.domain.ReservationPolicy;
import br.com.reservas.reservation.domain.ReservationStatus;
import br.com.reservas.reservation.infra.ReservationRepository;
import br.com.reservas.settings.application.CondominiumSettingsService;
import br.com.reservas.settings.application.RuleSettingsSnapshot;
import br.com.reservas.shared.error.BusinessException;
import br.com.reservas.unit.application.UnitService;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * `GET /areas/{id}/availability` (docs/03, F4-4). Mora no módulo
 * `reservation` (não em `area`): é ele quem já sabe combinar horário de
 * funcionamento + {@link ReservationPolicy} + as próprias reservas/bloqueios
 * (a dependência `area` -&gt; `reservation` que o gateway/porta evitam é
 * justamente nessa direção que este serviço concentra tudo). `area` continua
 * só exportando {@link AreaQueryService#findBookable(UUID)}, sem precisar de
 * um método novo.
 */
@Service
public class AvailabilityService {

    private static final int MAX_RANGE_DAYS = 62;
    private static final List<ReservationStatus> ACTIVE_STATUSES =
        List.of(ReservationStatus.PENDING_PAYMENT, ReservationStatus.CONFIRMED);

    private final AreaQueryService areaQueryService;
    private final CondominiumSettingsService settingsService;
    private final ReservationRepository reservations;
    private final UnitService unitService;
    private final ReservationService reservationService;
    private final Clock clock;

    public AvailabilityService(AreaQueryService areaQueryService, CondominiumSettingsService settingsService,
        ReservationRepository reservations, UnitService unitService, ReservationService reservationService,
        Clock clock) {
        this.areaQueryService = areaQueryService;
        this.settingsService = settingsService;
        this.reservations = reservations;
        this.unitService = unitService;
        this.reservationService = reservationService;
        this.clock = clock;
    }

    @Transactional
    public List<AvailabilityDay> forArea(UUID areaId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "O intervalo entre `from` e `to` precisa ser válido e não pode passar de 62 dias.");
        }
        AreaBookingInfo area = areaQueryService.findBookable(areaId)
            .orElseThrow(() -> new EntityNotFoundException("Área não encontrada: " + areaId));
        RuleSettingsSnapshot rules = settingsService.current();
        ZoneId zone = ZoneId.of(rules.timezone());
        ZonedDateTime now = ZonedDateTime.now(clock.withZone(zone));
        // RN-31: uma pendente vencida some da lista de "ocupado" só depois de virar CANCELLED aqui.
        reservationService.expirePendingIfNeeded(now.toInstant());

        Instant rangeStart = from.atStartOfDay(zone).toInstant();
        Instant rangeEnd = to.plusDays(1).atStartOfDay(zone).toInstant();
        List<Reservation> inRange = reservations.findActiveInRange(areaId, ACTIVE_STATUSES, rangeStart, rangeEnd);
        Map<LocalDate, List<Reservation>> byDate = inRange.stream()
            .collect(Collectors.groupingBy(r -> r.getStartAt().atZone(zone).toLocalDate()));
        Map<UUID, String> unitIdentifiersById = unitService.identifiersByIds(
            inRange.stream().map(Reservation::getUnitId).filter(java.util.Objects::nonNull).distinct().toList());

        List<AvailabilityDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            days.add(dayFor(date, area, rules, now, zone, byDate.getOrDefault(date, List.of()), unitIdentifiersById));
        }
        return days;
    }

    private AvailabilityDay dayFor(LocalDate date, AreaBookingInfo area, RuleSettingsSnapshot rules,
        ZonedDateTime now, ZoneId zone, List<Reservation> dayReservations, Map<UUID, String> unitIdentifiersById) {
        Optional<OpeningHoursRange> hours = area.hoursForDay(date.getDayOfWeek().getValue());
        Optional<ReservationPolicy.DayReason> reason = ReservationPolicy.checkDay(date, now, rules, area);
        List<BusyEntry> busy = dayReservations.stream().map(r -> toBusyEntry(r, zone, unitIdentifiersById)).toList();
        return new AvailabilityDay(date, hours.isPresent(), hours.map(OpeningHoursRange::openTime).orElse(null),
            hours.map(OpeningHoursRange::closeTime).orElse(null), reason.isEmpty(),
            reason.map(ReservationPolicy.DayReason::code).orElse(null), busy);
    }

    private BusyEntry toBusyEntry(Reservation r, ZoneId zone, Map<UUID, String> unitIdentifiersById) {
        // RF-RES-10: bloqueio (`kind = BLOCK`) não tem `unitId`; `Map.of()` (mapa vazio) lança
        // NullPointerException num `get(null)`, então precisa checar antes, não só confiar no `Map#get`.
        String unitIdentifier = r.getUnitId() == null ? null : unitIdentifiersById.get(r.getUnitId());
        ZonedDateTime start = r.getStartAt().atZone(zone);
        ZonedDateTime end = r.getEndAt().atZone(zone);
        return new BusyEntry(start.toLocalTime(), end.toLocalTime(), r.getKind().name(), r.getId(), r.getCode(),
            unitIdentifier, r.getStatus().name());
    }
}
