package br.com.reservas.reservation.domain;

/** `reservation.status` (V6, RN-32): transições permitidas ficam no serviço, não aqui. */
public enum ReservationStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    CANCELLED
}
