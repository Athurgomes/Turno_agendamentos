package br.com.reservas.reservation.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** Um dia de `GET /areas/{id}/availability` (docs/03, F4-4). */
public record AvailabilityDay(LocalDate date, boolean open, LocalTime openTime, LocalTime closeTime,
    boolean bookable, String notBookableReason, List<BusyEntry> busy) {
}
