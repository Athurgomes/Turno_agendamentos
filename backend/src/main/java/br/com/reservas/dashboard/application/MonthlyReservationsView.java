package br.com.reservas.dashboard.application;

/** Um item da série de 12 meses de `GET /dashboard/reservations-by-month` (RF-DAS-02). */
public record MonthlyReservationsView(String month, long total, long confirmed, long pendingPayment,
    long cancelled) {
}
