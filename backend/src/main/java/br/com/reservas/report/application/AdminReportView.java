package br.com.reservas.report.application;

import java.math.BigDecimal;
import java.util.UUID;

/** `AdminReportDto` (docs/03, D-50): {@link ReportView} + o que só S/A enxerga. */
public record AdminReportView(ReportView base, UUID unitId, String unitIdentifier, String residentPhone,
    String whatsappContactUrl, BigDecimal maintenanceCost) {
}
