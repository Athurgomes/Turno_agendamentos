package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.ReservationView;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * `ReservationDto` (docs/03, D-49): visão da própria unidade. `date`,
 * `startTime`, `endTime`, `completed` (RN-32), `statusReason` (RN-29) e
 * `canCancel` (RN-30, só o prazo — o cancelamento em si é F4-6) e `canReport`
 * (RN-34, F6) já vêm resolvidos de {@link ReservationView} (`application`).
 */
public record ReservationDto(UUID id, String code, String kind, UUID areaId, String areaName, LocalDate date,
    LocalTime startTime, LocalTime endTime, UUID residentId, String residentName, int guests, String notes,
    String status, boolean completed, String statusReason, String cancelledBy, boolean requiresPayment,
    BigDecimal price, Instant createdAt, boolean canCancel, boolean canReport, String whatsappPaymentUrl) {

    public static ReservationDto of(ReservationView v) {
        return new ReservationDto(v.id(), v.code(), v.kind(), v.areaId(), v.areaName(), v.date(), v.startTime(),
            v.endTime(), v.residentId(), v.residentName(), v.guests(), v.notes(), v.status(), v.completed(),
            v.statusReason(), v.cancelledBy(), v.requiresPayment(), v.price(), v.createdAt(), v.canCancel(),
            v.canReport(), v.whatsappPaymentUrl());
    }
}
