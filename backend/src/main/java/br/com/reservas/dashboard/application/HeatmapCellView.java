package br.com.reservas.dashboard.application;

/** Uma célula de `GET /dashboard/demand-heatmap` (RF-DAS-02): 1 = segunda … 7 = domingo. */
public record HeatmapCellView(int dayOfWeek, int hour, long count) {
}
