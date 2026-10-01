package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.HeatmapCellView;

/** Uma célula de `GET /dashboard/demand-heatmap` (RF-DAS-02): 1 = segunda … 7 = domingo. */
public record HeatmapCellDto(int dayOfWeek, int hour, long count) {

    public static HeatmapCellDto of(HeatmapCellView view) {
        return new HeatmapCellDto(view.dayOfWeek(), view.hour(), view.count());
    }
}
