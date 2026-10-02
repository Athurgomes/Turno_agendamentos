package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.OpenReportsView;
import java.util.List;

/** `openReports` de `GET /dashboard/home` (F7-1, RF-SIN-01). */
public record OpenReportsDto(long count, List<OpenReportItemDto> items) {

    public static OpenReportsDto of(OpenReportsView view) {
        return new OpenReportsDto(view.count(), view.items().stream().map(OpenReportItemDto::of).toList());
    }
}
