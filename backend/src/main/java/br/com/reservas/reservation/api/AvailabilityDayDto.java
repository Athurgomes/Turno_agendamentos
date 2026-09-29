package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.AvailabilityDay;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Um dia de `GET /areas/{id}/availability` (docs/03, F4-4). */
public record AvailabilityDayDto(LocalDate date, boolean open, LocalTime openTime, LocalTime closeTime,
    boolean bookable, String notBookableReason, List<BusyDto> busy) {

    public static AvailabilityDayDto of(AvailabilityDay day, boolean includeAdminFields) {
        return new AvailabilityDayDto(day.date(), day.open(), day.openTime(), day.closeTime(), day.bookable(),
            day.notBookableReason(), day.busy().stream().map(b -> BusyDto.of(b, includeAdminFields)).toList());
    }
}
