package br.com.reservas.dashboard.infra;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Uma linha de `GET /exports/reservations` (F8-2, docs/03): reservas e
 * bloqueios do período, já no fuso do condomínio.
 */
public record ReservationExportRow(String code, String kind, String areaName, String unitIdentifier,
    String residentName, LocalDateTime start, LocalDateTime end, Integer guests, String status, String cancelledBy,
    String reason, BigDecimal amount) {
}
