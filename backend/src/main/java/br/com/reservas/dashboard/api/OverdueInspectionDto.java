package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.OverdueInspectionView;
import java.time.LocalDate;
import java.util.UUID;

/** Item de `overdueInspections` em `GET /dashboard/home` (F7-1, RF-SIN-01). */
public record OverdueInspectionDto(UUID areaId, String areaName, String status, LocalDate lastInspectionAt,
    Long daysSinceInspection) {

    public static OverdueInspectionDto of(OverdueInspectionView view) {
        return new OverdueInspectionDto(view.areaId(), view.areaName(), view.status(), view.lastInspectionAt(),
            view.daysSinceInspection());
    }
}
