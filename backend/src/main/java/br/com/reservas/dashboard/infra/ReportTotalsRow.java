package br.com.reservas.dashboard.infra;

import java.math.BigDecimal;

/** Linha agregada de reports no período (D-61: 1 consulta, `FILTER`). */
public record ReportTotalsRow(long opened, long resolved, long open, BigDecimal averageResolutionHours,
    BigDecimal maintenanceCost) {
}
