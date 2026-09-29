package br.com.reservas.area.application;

import br.com.reservas.shared.reservation.ReservationSummary;
import java.util.List;

/** RN-16: `409 AREA_HAS_FUTURE_RESERVATIONS` ao mudar status/excluir area sem confirmar o cancelamento em lote. */
public class AreaHasFutureReservationsException extends RuntimeException {

    private final List<ReservationSummary> affectedReservations;

    public AreaHasFutureReservationsException(List<ReservationSummary> affectedReservations) {
        super("A area tem reservas futuras ativas.");
        this.affectedReservations = affectedReservations;
    }

    public List<ReservationSummary> affectedReservations() {
        return affectedReservations;
    }
}
