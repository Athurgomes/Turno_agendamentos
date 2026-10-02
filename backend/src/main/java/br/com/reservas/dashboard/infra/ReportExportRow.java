package br.com.reservas.dashboard.infra;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Uma linha de `GET /exports/reports` (F8-2, docs/03): reports criados no período. */
public record ReportExportRow(String code, String areaName, String unitIdentifier, String category, String status,
    LocalDateTime openedAt, LocalDateTime resolvedAt, BigDecimal maintenanceCost) {
}
