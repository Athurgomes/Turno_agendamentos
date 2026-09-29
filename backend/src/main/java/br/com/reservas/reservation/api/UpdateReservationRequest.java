package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.UpdateReservationCommand;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * `PUT /reservations/{id}` (ADMIN, RN-27/RN-28): `areaId`/`date`/`startTime`/`endTime` ausentes/null
 * mantêm o valor atual; `justification` é obrigatório (contrato, docs/03) — `@NotNull` cobre a
 * ausência (400 `VALIDATION_ERROR`), o tamanho mínimo (RN-27, &lt; 10 caracteres) continua
 * `422 JUSTIFICATION_REQUIRED` no serviço.
 */
public record UpdateReservationRequest(UUID areaId, LocalDate date, LocalTime startTime, LocalTime endTime,
    @NotNull String justification) {

    public UpdateReservationCommand toCommand() {
        return new UpdateReservationCommand(areaId, date, startTime, endTime, justification);
    }
}
