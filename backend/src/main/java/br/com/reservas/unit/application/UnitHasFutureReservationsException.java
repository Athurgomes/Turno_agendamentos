package br.com.reservas.unit.application;

import br.com.reservas.shared.reservation.ReservationSummary;
import java.util.List;

/**
 * P-11: `409 UNIT_HAS_FUTURE_RESERVATIONS` ao tentar desativar unidade com
 * reservas futuras ativas; a lista vai no {@code ProblemDetail} (mesmo campo
 * `affectedReservations` usado pelo 409 de area, D-44). Ate a F4,
 * {@link FutureReservationsPort} sempre devolve lista vazia, entao esta
 * excecao nunca e lancada na pratica ainda.
 */
public class UnitHasFutureReservationsException extends RuntimeException {

    private final List<ReservationSummary> affectedReservations;

    public UnitHasFutureReservationsException(List<ReservationSummary> affectedReservations) {
        super("A unidade tem reservas futuras ativas.");
        this.affectedReservations = affectedReservations;
    }

    public List<ReservationSummary> affectedReservations() {
        return affectedReservations;
    }
}
