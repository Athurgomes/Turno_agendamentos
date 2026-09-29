package br.com.reservas.area.api;

import br.com.reservas.area.domain.InspectionCondition;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** `data` (JSON) de `POST /areas/{id}/inspections` (docs/03, RF-ARE-07). */
public record CreateInspectionRequest(@NotNull LocalDate inspectedAt, @NotNull InspectionCondition overallCondition,
    String notes) {
}
