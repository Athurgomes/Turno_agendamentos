package br.com.reservas.area.api;

import br.com.reservas.area.application.OpeningHoursInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;

public record OpeningHoursRequest(@Min(1) @Max(7) int dayOfWeek, @NotNull LocalTime openTime,
    @NotNull LocalTime closeTime) {

    public OpeningHoursInput toInput() {
        return new OpeningHoursInput(dayOfWeek, openTime, closeTime);
    }
}
