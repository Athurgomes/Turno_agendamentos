package br.com.reservas.reservation.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** `GET /reservations/{id}/events` (S/A, RF-RES-09): um item do histórico, em ordem cronológica. */
public record ReservationEventView(String type, Instant occurredAt, ActorView actor, String justification,
    Map<String, Object> changes) {

    /** Ator do evento; `null` quando o evento não tem um responsável humano (ex.: expiração automática, F5). */
    public record ActorView(UUID id, String name, String role) {
    }
}
