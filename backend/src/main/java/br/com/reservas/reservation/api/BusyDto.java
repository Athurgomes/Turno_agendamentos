package br.com.reservas.reservation.api;

import br.com.reservas.reservation.application.BusyEntry;
import java.time.LocalTime;
import java.util.UUID;

/**
 * `busy` de `GET /areas/{id}/availability` (docs/03): para UNIT só
 * {@code startTime}/{@code endTime}/{@code kind}; para S/A também
 * {@code reservationId}, {@code code}, {@code unitIdentifier}, {@code status}.
 */
public record BusyDto(LocalTime startTime, LocalTime endTime, String kind, UUID reservationId, String code,
    String unitIdentifier, String status) {

    public static BusyDto of(BusyEntry entry, boolean includeAdminFields) {
        if (!includeAdminFields) {
            return new BusyDto(entry.startTime(), entry.endTime(), entry.kind(), null, null, null, null);
        }
        return new BusyDto(entry.startTime(), entry.endTime(), entry.kind(), entry.reservationId(), entry.code(),
            entry.unitIdentifier(), entry.status());
    }
}
