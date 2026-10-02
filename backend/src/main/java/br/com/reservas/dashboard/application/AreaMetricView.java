package br.com.reservas.dashboard.application;

import java.math.BigDecimal;
import java.util.UUID;

/** Um item de `GET /dashboard/areas` (RF-DAS-02): reservas, ocupação, reports e custo por área. */
public record AreaMetricView(UUID areaId, String areaName, String category, String status, long reservations,
    BigDecimal reservedHours, BigDecimal availableHours, BigDecimal occupancyRate, long reports,
    BigDecimal maintenanceCost) {
}
