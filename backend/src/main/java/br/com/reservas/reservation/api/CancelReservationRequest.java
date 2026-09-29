package br.com.reservas.reservation.api;

/** `POST /reservations/{id}/cancel` (ADMIN, RN-27): justificativa validada em {@code ReservationService}. */
public record CancelReservationRequest(String justification) {
}
