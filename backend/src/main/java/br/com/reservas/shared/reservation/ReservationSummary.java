package br.com.reservas.shared.reservation;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * `ReservationSummary` (docs/03): usado no 409 de exclusao/status de area
 * (D-44) e no `affectedReservations` de exclusao/transferencia de unidade
 * (P-11, RN-10). Movido para `shared` (F3) porque agora dois modulos (`unit`
 * e `area`) o consomem via uma porta propria; o modulo `reservation` (F4) e
 * quem vai produzir estes valores de verdade.
 */
public record ReservationSummary(UUID id, String code, String kind, UUID areaId, String areaName,
    String unitIdentifier, String residentName, LocalDate date, LocalTime startTime, LocalTime endTime,
    String status) {
}
