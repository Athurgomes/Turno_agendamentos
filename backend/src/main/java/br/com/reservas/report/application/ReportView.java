package br.com.reservas.report.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * `ReportDto` (docs/03, D-50): visão da própria unidade. {@code comments} já
 * vem filtrado para só `visibleToResident = true` quando montado para a
 * unidade (`ReportService`); a visão S/A embrulha este tipo em
 * {@link AdminReportView} com todos os comentários.
 */
public record ReportView(UUID id, String code, UUID reservationId, String reservationCode, UUID areaId,
    String areaName, LocalDate reservationDate, String category, String description, String residentName,
    String status, String statusReason, Instant createdAt, Instant resolvedAt, List<ReportPhotoView> photos,
    List<ReportCommentView> comments) {
}
