package br.com.reservas.report.api;

import br.com.reservas.report.application.CreateReportCommand;
import br.com.reservas.report.domain.ReportCategory;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * `data` (JSON) de `POST /me/reservations/{id}/reports` (docs/03, RN-34/RN-35).
 * `description` não tem `@NotBlank`/`@Size` de propósito: o mínimo de 10
 * caracteres é regra de negócio (`422 VALIDATION_ERROR` via
 * {@code ReportService}), não erro de Bean Validation (`400`).
 */
public record CreateReportRequest(@NotNull ReportCategory category, String description,
    @NotNull UUID residentId) {

    public CreateReportCommand toCommand() {
        return new CreateReportCommand(category, description, residentId);
    }
}
