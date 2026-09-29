package br.com.reservas.unit.api;

import br.com.reservas.shared.reservation.ReservationSummary;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** `ReservationSummary` (docs/03): lista de reservas afetadas em desativacao/transferencia de unidade. */
public record ReservationSummaryDto(UUID id, String code, String kind, UUID areaId, String areaName,
    String unitIdentifier, String residentName, LocalDate date, LocalTime startTime, LocalTime endTime,
    String status) {

    public static ReservationSummaryDto from(ReservationSummary summary) {
        return new ReservationSummaryDto(summary.id(), summary.code(), summary.kind(), summary.areaId(),
            summary.areaName(), summary.unitIdentifier(), summary.residentName(), summary.date(),
            summary.startTime(), summary.endTime(), summary.status());
    }
}
