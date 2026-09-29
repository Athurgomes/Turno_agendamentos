package br.com.reservas.shared.reservation;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-34: 4 bordas da janela de report (dia da reserva 00:00 até D+7 23:59:59). */
class ReportWindowPolicyTest {

    private static final LocalDate RESERVATION_DATE = LocalDate.of(2026, 11, 11);
    private static final int WINDOW_DAYS = 7;

    @Test
    @DisplayName("RN-34: D-1 23:59 (dia anterior a reserva) -> janela fechada")
    void dayBeforeIsClosed() {
        LocalDate today = RESERVATION_DATE.minusDays(1);

        assertThat(ReportWindowPolicy.isOpen(RESERVATION_DATE, today, WINDOW_DAYS)).isFalse();
    }

    @Test
    @DisplayName("RN-34: D 00:00 (dia da reserva) -> janela aberta")
    void reservationDayIsOpen() {
        assertThat(ReportWindowPolicy.isOpen(RESERVATION_DATE, RESERVATION_DATE, WINDOW_DAYS)).isTrue();
    }

    @Test
    @DisplayName("RN-34: D+7 23:59:59 (ultimo dia da janela) -> ainda aberta")
    void lastDayOfWindowIsOpen() {
        LocalDate lastDay = RESERVATION_DATE.plusDays(WINDOW_DAYS);

        assertThat(ReportWindowPolicy.isOpen(RESERVATION_DATE, lastDay, WINDOW_DAYS)).isTrue();
    }

    @Test
    @DisplayName("RN-34: D+8 00:00 (um dia depois do fim da janela) -> fechada")
    void dayAfterWindowIsClosed() {
        LocalDate dayAfter = RESERVATION_DATE.plusDays(WINDOW_DAYS + 1);

        assertThat(ReportWindowPolicy.isOpen(RESERVATION_DATE, dayAfter, WINDOW_DAYS)).isFalse();
    }
}
