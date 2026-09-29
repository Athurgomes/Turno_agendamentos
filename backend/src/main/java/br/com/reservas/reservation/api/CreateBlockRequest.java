package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.CreateBlockCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** `POST /blocks` (S/A, RN-33): `reason` vira `notes` do bloqueio (D-49). */
public record CreateBlockRequest(@NotNull UUID areaId, @NotNull LocalDate date, @NotNull LocalTime startTime,
    @NotNull LocalTime endTime, @NotBlank @Size(max = 500) String reason) {

    public CreateBlockCommand toCommand() {
        return new CreateBlockCommand(areaId, date, startTime, endTime, reason);
    }
}
