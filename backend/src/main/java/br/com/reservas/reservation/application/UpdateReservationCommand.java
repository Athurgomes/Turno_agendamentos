package br.com.reservas.reservation.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** `PUT /reservations/{id}` (ADMIN, RN-27/RN-28): campos ausentes/null mantêm o valor atual. */
public record UpdateReservationCommand(UUID areaId, LocalDate date, LocalTime startTime, LocalTime endTime,
    String justification) {
}
