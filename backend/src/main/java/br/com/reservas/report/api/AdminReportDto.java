package br.com.reservas.report.api;

import br.com.reservas.report.application.AdminReportView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** `AdminReportDto` (docs/03, D-50) = {@link ReportDto} + o que só S/A enxerga, com todos os comentários. */
public record AdminReportDto(UUID id, String code, UUID reservationId, String reservationCode, UUID areaId,
    String areaName, LocalDate reservationDate, String category, String description, String residentName,
    String status, String statusReason, Instant createdAt, Instant resolvedAt, List<ReportPhotoDto> photos,
    List<ReportCommentDto> comments, UUID unitId, String unitIdentifier, String residentPhone,
    String whatsappContactUrl, BigDecimal maintenanceCost) {

    public static AdminReportDto of(AdminReportView v) {
        var b = v.base();
        return new AdminReportDto(b.id(), b.code(), b.reservationId(), b.reservationCode(), b.areaId(), b.areaName(),
            b.reservationDate(), b.category(), b.description(), b.residentName(), b.status(), b.statusReason(),
            b.createdAt(), b.resolvedAt(), b.photos().stream().map(ReportPhotoDto::of).toList(),
            b.comments().stream().map(ReportCommentDto::of).toList(), v.unitId(), v.unitIdentifier(),
            v.residentPhone(), v.whatsappContactUrl(), v.maintenanceCost());
    }
}
