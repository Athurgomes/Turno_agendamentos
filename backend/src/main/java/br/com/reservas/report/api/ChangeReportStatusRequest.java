package br.com.reservas.report.api;

import br.com.reservas.report.application.ChangeReportStatusCommand;
import br.com.reservas.report.domain.ReportStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/** `PATCH /reports/{id}/status` (S/A, docs/03, RN-36/RN-37). */
public record ChangeReportStatusRequest(@NotNull ReportStatus status, String justification,
    @DecimalMin(value = "0", message = "O custo de manutenção não pode ser negativo.") BigDecimal maintenanceCost) {

    public ChangeReportStatusCommand toCommand() {
        return new ChangeReportStatusCommand(status, justification, maintenanceCost);
    }
}
