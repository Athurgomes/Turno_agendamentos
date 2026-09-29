package br.com.reservas.reservation.application;

import br.com.reservas.reservation.domain.ReservationKind;
import br.com.reservas.reservation.domain.ReservationStatus;
import java.time.LocalDate;
import java.util.UUID;

/** `GET /reservations` (S/A, D-53): filtros da agenda, todos opcionais. */
public record ReservationFilter(UUID areaId, LocalDate from, LocalDate to, ReservationStatus status, UUID unitId,
    String unitIdentifier, ReservationKind kind) {
}
