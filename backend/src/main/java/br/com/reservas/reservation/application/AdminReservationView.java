package br.com.reservas.reservation.application;

import br.com.reservas.reservation.domain.Reservation;
import br.com.reservas.reservation.domain.ReservationKind;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

/**
 * `AdminReservationDto` (docs/03, D-49): {@link ReservationView} + o que só
 * S/A enxerga (unidade, telefone, link de contato, datas de
 * cancelamento/confirmação). Para `kind = BLOCK`, os campos de unidade e
 * morador saem `null` (sem morador a identificar).
 */
public record AdminReservationView(ReservationView base, UUID unitId, String unitIdentifier, String residentPhone,
    String whatsappContactUrl, Instant cancelledAt, Instant paymentConfirmedAt) {

    public static AdminReservationView of(Reservation r, String areaName, ZoneId zone, Instant now,
        int residentCancelDeadlineHours, int reportWindowDays, String whatsappPaymentUrl, String unitIdentifier) {
        ReservationView base = ReservationView.of(r, areaName, zone, now, residentCancelDeadlineHours,
            reportWindowDays, whatsappPaymentUrl);
        boolean isBlock = r.getKind() == ReservationKind.BLOCK;
        String phone = isBlock ? null : r.getResidentPhoneSnapshot();
        String whatsappContactUrl = phone == null ? null : "https://wa.me/" + phone;
        return new AdminReservationView(base, isBlock ? null : r.getUnitId(), isBlock ? null : unitIdentifier, phone,
            whatsappContactUrl, r.getCancelledAt(), r.getPaymentConfirmedAt());
    }
}
