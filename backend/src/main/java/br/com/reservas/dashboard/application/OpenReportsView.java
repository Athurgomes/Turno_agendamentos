package br.com.reservas.dashboard.application;

import java.util.List;

/** `openReports` de `GET /dashboard/home` (F7-1, RF-SIN-01): contador + os 5 mais antigos. */
public record OpenReportsView(long count, List<OpenReportItemView> items) {
}
