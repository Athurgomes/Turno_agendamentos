package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.AdminReservationView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * `AdminReservationDto` (docs/03, D-49) = {@link ReservationDto} + o que só
 * S/A enxerga. Para `kind = BLOCK`, `unitId`/`unitIdentifier`/`residentPhone`/
 * `whatsappContactUrl` saem `null` (sem morador a identificar).
 */
public record AdminReservationDto(UUID id, String code, String kind, UUID areaId, String areaName, LocalDate date,
    LocalTime startTime, LocalTime endTime, UUID residentId, String residentName, int guests, String notes,
    String status, boolean completed, String statusReason, String cancelledBy, boolean requiresPayment,
    BigDecimal price, Instant createdAt, boolean canCancel, boolean canReport, String whatsappPaymentUrl,
    UUID unitId, String unitIdentifier, String residentPhone, String whatsappContactUrl, Instant cancelledAt,
    Instant paymentConfirmedAt) {

    public static AdminReservationDto of(AdminReservationView v) {
        var b = v.base();
        return new AdminReservationDto(b.id(), b.code(), b.kind(), b.areaId(), b.areaName(), b.date(), b.startTime(),
            b.endTime(), b.residentId(), b.residentName(), b.guests(), b.notes(), b.status(), b.completed(),
            b.statusReason(), b.cancelledBy(), b.requiresPayment(), b.price(), b.createdAt(), b.canCancel(), false,
            b.whatsappPaymentUrl(), v.unitId(), v.unitIdentifier(), v.residentPhone(), v.whatsappContactUrl(),
            v.cancelledAt(), v.paymentConfirmedAt());
    }
}
