package br.com.reservas.dashboard.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Um item de `GET /dashboard/top-units` (RF-DAS-02): até 10, ordenado por `reservations` desc. */
public record TopUnitView(UUID unitId, String unitIdentifier, long reservations, BigDecimal reservedHours) {
}
