package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.AreaMetricView;
import java.math.BigDecimal;
import java.util.UUID;

/** Um item de `GET /dashboard/areas` (RF-DAS-02): reservas, ocupação, reports e custo por área. */
public record AreaMetricDto(UUID areaId, String areaName, String category, String status, long reservations,
    BigDecimal reservedHours, BigDecimal availableHours, BigDecimal occupancyRate, long reports,
    BigDecimal maintenanceCost) {

    public static AreaMetricDto of(AreaMetricView view) {
        return new AreaMetricDto(view.areaId(), view.areaName(), view.category(), view.status(),
            view.reservations(), view.reservedHours(), view.availableHours(), view.occupancyRate(), view.reports(),
            view.maintenanceCost());
    }
}
