package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.TopUnitView;
import java.math.BigDecimal;
import java.util.UUID;

/** Um item de `GET /dashboard/top-units` (RF-DAS-02): até 10, ordenado por `reservations` desc. */
public record TopUnitDto(UUID unitId, String unitIdentifier, long reservations, BigDecimal reservedHours) {

    public static TopUnitDto of(TopUnitView view) {
        return new TopUnitDto(view.unitId(), view.unitIdentifier(), view.reservations(), view.reservedHours());
    }
}
