package br.com.reservas.report.api;

import br.com.reservas.report.application.ReportView;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * `ReportDto` (docs/03, D-50): visão da própria unidade. {@code comments} já
 * vem filtrado para `visibleToResident = true` por {@link ReportView}.
 */
public record ReportDto(UUID id, String code, UUID reservationId, String reservationCode, UUID areaId,
    String areaName, LocalDate reservationDate, String category, String description, String residentName,
    String status, String statusReason, Instant createdAt, Instant resolvedAt, List<ReportPhotoDto> photos,
    List<ReportCommentDto> comments) {

    public static ReportDto of(ReportView v) {
        return new ReportDto(v.id(), v.code(), v.reservationId(), v.reservationCode(), v.areaId(), v.areaName(),
            v.reservationDate(), v.category(), v.description(), v.residentName(), v.status(), v.statusReason(),
            v.createdAt(), v.resolvedAt(), v.photos().stream().map(ReportPhotoDto::of).toList(),
            v.comments().stream().map(ReportCommentDto::of).toList());
    }
}
