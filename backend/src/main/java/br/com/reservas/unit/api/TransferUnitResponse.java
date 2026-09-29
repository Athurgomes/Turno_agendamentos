package br.com.reservas.unit.api;

import java.util.List;

public record TransferUnitResponse(CredentialsDto credentials, List<ReservationSummaryDto> affectedReservations) {
}
