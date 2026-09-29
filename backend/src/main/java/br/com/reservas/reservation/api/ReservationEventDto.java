package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.ReservationEventView;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** `GET /reservations/{id}/events` (S/A, RF-RES-09). */
public record ReservationEventDto(String type, Instant occurredAt, ActorDto actor, String justification,
    Map<String, Object> changes) {

    public record ActorDto(UUID id, String name, String role) {
    }

    public static ReservationEventDto of(ReservationEventView v) {
        ActorDto actor = v.actor() == null ? null
            : new ActorDto(v.actor().id(), v.actor().name(), v.actor().role());
        return new ReservationEventDto(v.type(), v.occurredAt(), actor, v.justification(), v.changes());
    }
}
