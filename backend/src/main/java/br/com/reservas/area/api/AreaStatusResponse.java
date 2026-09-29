package br.com.reservas.area.api;

/** `{ area, cancelledReservations }` (docs/03: resposta de `PATCH /areas/{id}/status`). */
public record AreaStatusResponse(AreaDetailDto area, int cancelledReservations) {
}
