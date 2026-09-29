package br.com.reservas.report.application;

import br.com.reservas.report.domain.ReportStatus;
import java.math.BigDecimal;

/** `PATCH /reports/{id}/status` (S/A, RN-36/RN-37). */
public record ChangeReportStatusCommand(ReportStatus status, String justification, BigDecimal maintenanceCost) {
}
