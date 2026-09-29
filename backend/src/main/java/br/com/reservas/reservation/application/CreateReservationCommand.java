package br.com.reservas.reservation.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** RF-RES-02: dados do formulário de reserva (a unidade vem do token, RN-01). */
public record CreateReservationCommand(UUID areaId, LocalDate date, LocalTime startTime, LocalTime endTime,
    UUID residentId, int guests, String notes) {
}
