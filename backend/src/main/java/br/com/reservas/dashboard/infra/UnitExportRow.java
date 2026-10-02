package br.com.reservas.dashboard.infra;

/** Uma linha de `GET /exports/units` (F8-2, docs/03): unidades não excluídas. */
public record UnitExportRow(String identifier, String block, String number, boolean active, long activeResidents,
    long reservations) {
}
