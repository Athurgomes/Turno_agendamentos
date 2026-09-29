package br.com.reservas.unit.application;

import br.com.reservas.shared.reservation.ReservationSummary;
import br.com.reservas.unit.domain.Resident;
import java.util.List;

public record TransferResult(List<Resident> residents, Credentials credentials,
    List<ReservationSummary> affectedReservations) {
}
