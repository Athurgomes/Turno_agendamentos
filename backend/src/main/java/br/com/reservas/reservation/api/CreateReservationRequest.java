package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.CreateReservationCommand;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** `POST /reservations` (docs/03): a unidade vem do token (RN-01), nunca do corpo. */
public record CreateReservationRequest(@NotNull UUID areaId, @NotNull LocalDate date, @NotNull LocalTime startTime,
    @NotNull LocalTime endTime, @NotNull UUID residentId, @NotNull @Min(1) Integer guests,
    @Size(max = 500) String notes) {

    public CreateReservationCommand toCommand() {
        return new CreateReservationCommand(areaId, date, startTime, endTime, residentId, guests, notes);
    }
}
