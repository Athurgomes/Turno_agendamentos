package br.com.reservas.area.api;

import br.com.reservas.area.domain.OpeningHours;
import java.time.LocalTime;

/** `OpeningHoursDto` (docs/03, D-44). */
public record OpeningHoursDto(int dayOfWeek, LocalTime openTime, LocalTime closeTime) {

    public static OpeningHoursDto from(OpeningHours hours) {
        return new OpeningHoursDto(hours.getDayOfWeek(), hours.getOpenTime(), hours.getCloseTime());
    }
}
