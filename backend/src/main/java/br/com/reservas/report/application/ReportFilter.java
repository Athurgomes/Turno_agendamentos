package br.com.reservas.report.application;

import br.com.reservas.report.domain.ReportCategory;
import br.com.reservas.report.domain.ReportStatus;
import java.time.LocalDate;
import java.util.UUID;

/** `GET /reports` (S/A): filtros da caixa de reports, todos opcionais. */
public record ReportFilter(ReportStatus status, UUID areaId, ReportCategory category, LocalDate from, LocalDate to) {
}
