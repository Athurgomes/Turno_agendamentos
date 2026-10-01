package br.com.reservas.dashboard.application;

import java.time.Instant;
import java.util.UUID;

/** Item de `openReports.items` em `GET /dashboard/home` (F7-1, RF-SIN-01). */
public record OpenReportItemView(UUID id, String code, String areaName, String unitIdentifier, String category,
    String status, Instant createdAt) {
}
