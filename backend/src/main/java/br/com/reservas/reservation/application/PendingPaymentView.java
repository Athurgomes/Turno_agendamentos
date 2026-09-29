package br.com.reservas.reservation.application;

/**
 * `GET /payments/pending` (ADMIN, RF-PAG-02): {@link AdminReservationView} + `within48h`
 * (início ≤ agora + 48h, pelo {@code Clock} da aplicação — usado pelo front para destacar
 * as pendências mais urgentes na aba "Confirmações").
 */
public record PendingPaymentView(AdminReservationView reservation, boolean within48h) {
}
