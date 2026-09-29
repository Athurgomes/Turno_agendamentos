package br.com.reservas.area.api;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** `InspectionDto` (docs/03, RF-ARE-07). */
public record InspectionDto(UUID id, LocalDate inspectedAt, String overallCondition, String notes,
    UploadedByDto author, List<PhotoDto> photos) {
}
