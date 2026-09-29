package br.com.reservas.report.application;

import br.com.reservas.report.domain.ReportCategory;
import java.util.UUID;

/** `POST /me/reservations/{id}/reports` (UNIT): parte `data` do multipart. */
public record CreateReportCommand(ReportCategory category, String description, UUID residentId) {
}
