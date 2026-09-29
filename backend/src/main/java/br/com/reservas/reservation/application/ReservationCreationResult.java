package br.com.reservas.reservation.application;

/** `POST /reservations` -&gt; `201 { reservation, whatsappPaymentUrl }` (docs/03). */
public record ReservationCreationResult(ReservationView reservation, String whatsappPaymentUrl) {
}
