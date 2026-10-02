package br.com.reservas.dashboard.api;

import br.com.reservas.dashboard.application.MonthlyReservationsView;

/** Um item da série de 12 meses de `GET /dashboard/reservations-by-month` (RF-DAS-02). */
public record MonthlyReservationsDto(String month, long total, long confirmed, long pendingPayment,
    long cancelled) {

    public static MonthlyReservationsDto of(MonthlyReservationsView view) {
        return new MonthlyReservationsDto(view.month(), view.total(), view.confirmed(), view.pendingPayment(),
            view.cancelled());
    }
}
