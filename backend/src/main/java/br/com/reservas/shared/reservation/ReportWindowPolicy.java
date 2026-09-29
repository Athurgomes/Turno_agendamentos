package br.com.reservas.shared.reservation;

import java.time.LocalDate;

/**
 * RN-34: janela para abrir um report sobre uma reserva `CONFIRMED`, de
 * 00:00 do dia da reserva (fuso do condomínio) até 23:59:59 do dia D +
 * {@code report_window_days} (default 7, `condominium_settings`).
 *
 * <p>Domínio puro (sem Spring, sem `Clock`): compara só as datas locais (já
 * resolvidas pelo chamador no fuso do condomínio), o que cobre a janela
 * inteira do dia final sem precisar do horário exato — testável com
 * {@code LocalDate} literais nas bordas.
 *
 * <p>Vive em `shared` (não em `reservation` nem em `report`) porque os dois
 * módulos precisam da mesma regra sem um depender do outro (CLAUDE.md §5,
 * codebase-design): {@code reservation.application.ReservationView} usa para
 * `canReport`; {@code report.application.ReportService} usa para validar a
 * criação de um report.
 */
public final class ReportWindowPolicy {

    private ReportWindowPolicy() {
    }

    public static boolean isOpen(LocalDate reservationDate, LocalDate today, int reportWindowDays) {
        if (today.isBefore(reservationDate)) {
            return false;
        }
        LocalDate windowEnd = reservationDate.plusDays(reportWindowDays);
        return !today.isAfter(windowEnd);
    }
}
