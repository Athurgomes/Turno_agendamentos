package br.com.reservas.reservation.application;

import br.com.reservas.reservation.domain.Reservation;
import br.com.reservas.reservation.domain.ReservationKind;
import br.com.reservas.reservation.domain.ReservationStatus;
import br.com.reservas.shared.reservation.ReportWindowPolicy;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Projeção de leitura de uma {@link Reservation} (RF-RES-01..05): tudo que a
 * camada `api` precisa para montar `ReservationDto`, já resolvido aqui
 * (fuso do condomínio, RN-29/RN-30/RN-32/RN-34) para a `application` nunca
 * importar um tipo de `api` (CLAUDE.md §5). `canReport` (F6) usa a mesma
 * {@link ReportWindowPolicy} (`shared`) que o módulo `report` usa para
 * validar a criação de um report, para as duas nunca divergirem.
 */
public record ReservationView(UUID id, String code, String kind, UUID areaId, String areaName, LocalDate date,
    LocalTime startTime, LocalTime endTime, UUID residentId, String residentName, int guests, String notes,
    String status, boolean completed, String statusReason, String cancelledBy, boolean requiresPayment,
    BigDecimal price, Instant createdAt, boolean canCancel, boolean canReport, String whatsappPaymentUrl) {

    public static ReservationView of(Reservation reservation, String areaName, ZoneId zone, Instant now,
        int residentCancelDeadlineHours, int reportWindowDays, String whatsappPaymentUrl) {
        ZonedDateTime start = reservation.getStartAt().atZone(zone);
        ZonedDateTime end = reservation.getEndAt().atZone(zone);
        return new ReservationView(reservation.getId(), reservation.getCode(), reservation.getKind().name(),
            reservation.getAreaId(), areaName, start.toLocalDate(), start.toLocalTime(), end.toLocalTime(),
            reservation.getResidentId(), reservation.getResidentNameSnapshot(),
            // RF-RES-10: bloqueio (`kind = BLOCK`) não tem convidados (`guests` fica `null` no banco).
            reservation.getGuests() == null ? 0 : reservation.getGuests(),
            reservation.getNotes(), reservation.getStatus().name(), isCompleted(reservation, now),
            statusReason(reservation), reservation.getCancelledBy(), reservation.isRequiresPaymentSnapshot(),
            reservation.getPriceSnapshot(), reservation.getCreatedAt(), canCancel(reservation, now,
                residentCancelDeadlineHours), canReport(reservation, start.toLocalDate(), now.atZone(zone)
                    .toLocalDate(), reportWindowDays), whatsappPaymentUrl);
    }

    // RN-34: só reserva (kind = BOOKING) CONFIRMED, dentro da janela.
    private static boolean canReport(Reservation reservation, LocalDate reservationDate, LocalDate today,
        int reportWindowDays) {
        return reservation.getKind() == ReservationKind.BOOKING && reservation.getStatus() == ReservationStatus.CONFIRMED
            && ReportWindowPolicy.isOpen(reservationDate, today, reportWindowDays);
    }

    // RN-32: "Realizada" na UI = CONFIRMED com fim no passado.
    private static boolean isCompleted(Reservation reservation, Instant now) {
        return reservation.getStatus() == ReservationStatus.CONFIRMED && reservation.getEndAt().isBefore(now);
    }

    // RN-29 (mensagem padrão por status). RN-27: uma justificativa de alteração do ADMIN grava
    // `statusReason` mesmo com a reserva ainda ativa e prevalece sobre a mensagem padrão de
    // PENDING_PAYMENT — CONFIRMED não tem mensagem padrão, então mostra a justificativa (se houver).
    private static String statusReason(Reservation reservation) {
        return switch (reservation.getStatus()) {
            case PENDING_PAYMENT -> reservation.getStatusReason() != null ? reservation.getStatusReason()
                : "Aguardando confirmação de pagamento pela administração.";
            case CONFIRMED -> reservation.getStatusReason();
            case CANCELLED -> reservation.getStatusReason();
        };
    }

    // RN-30: só o prazo (o cancelamento em si é F4-6); ativa e agora < início - prazo.
    private static boolean canCancel(Reservation reservation, Instant now, int residentCancelDeadlineHours) {
        boolean active = reservation.getStatus() == ReservationStatus.PENDING_PAYMENT
            || reservation.getStatus() == ReservationStatus.CONFIRMED;
        return active && now.isBefore(reservation.getStartAt().minus(residentCancelDeadlineHours, ChronoUnit.HOURS));
    }
}
