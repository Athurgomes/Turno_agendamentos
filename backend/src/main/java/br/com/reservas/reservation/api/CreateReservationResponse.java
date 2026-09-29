package br.com.reservas.reservation.api;

/** `POST /reservations` -&gt; `201 { reservation, whatsappPaymentUrl }` (docs/03). */
public record CreateReservationResponse(ReservationDto reservation, String whatsappPaymentUrl) {
}
