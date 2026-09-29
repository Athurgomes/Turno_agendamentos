package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.PendingPaymentView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * `GET /payments/pending` (ADMIN, docs/03, RF-PAG-02): {@link AdminReservationDto}
 * + `within48h`, achatado no mesmo objeto (o front usa `PendingPaymentDto extends
 * AdminReservationDto`, `frontend/src/shared/api/types.ts`).
 */
public record PendingPaymentDto(UUID id, String code, String kind, UUID areaId, String areaName, LocalDate date,
    LocalTime startTime, LocalTime endTime, UUID residentId, String residentName, int guests, String notes,
    String status, boolean completed, String statusReason, String cancelledBy, boolean requiresPayment,
    BigDecimal price, Instant createdAt, boolean canCancel, boolean canReport, String whatsappPaymentUrl,
    UUID unitId, String unitIdentifier, String residentPhone, String whatsappContactUrl, Instant cancelledAt,
    Instant paymentConfirmedAt, boolean within48h) {

    public static PendingPaymentDto of(PendingPaymentView v) {
        AdminReservationDto d = AdminReservationDto.of(v.reservation());
        return new PendingPaymentDto(d.id(), d.code(), d.kind(), d.areaId(), d.areaName(), d.date(), d.startTime(),
            d.endTime(), d.residentId(), d.residentName(), d.guests(), d.notes(), d.status(), d.completed(),
            d.statusReason(), d.cancelledBy(), d.requiresPayment(), d.price(), d.createdAt(), d.canCancel(),
            d.canReport(), d.whatsappPaymentUrl(), d.unitId(), d.unitIdentifier(), d.residentPhone(),
            d.whatsappContactUrl(), d.cancelledAt(), d.paymentConfirmedAt(), v.within48h());
    }
}
