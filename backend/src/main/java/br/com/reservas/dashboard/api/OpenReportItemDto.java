package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.OpenReportItemView;
import java.time.Instant;
import java.util.UUID;

/** Item de `openReports.items` em `GET /dashboard/home` (F7-1, RF-SIN-01). */
public record OpenReportItemDto(UUID id, String code, String areaName, String unitIdentifier, String category,
    String status, Instant createdAt) {

    public static OpenReportItemDto of(OpenReportItemView view) {
        return new OpenReportItemDto(view.id(), view.code(), view.areaName(), view.unitIdentifier(),
            view.category(), view.status(), view.createdAt());
    }
}
