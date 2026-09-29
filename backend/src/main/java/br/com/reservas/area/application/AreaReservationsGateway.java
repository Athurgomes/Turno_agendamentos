package br.com.reservas.area.application;

import br.com.reservas.shared.reservation.ReservationSummary;
import java.util.List;
import java.util.UUID;

/**
 * RN-16/D-44: porta para o modulo `reservation` informar reservas e
 * bloqueios futuros ativos de uma area e cancela-los em lote. Mesmo espirito
 * de {@code unit.application.FutureReservationsPort}; implementada por
 * {@code reservation.infra.AreaReservationsGatewayImpl} (F4).
 */
public interface AreaReservationsGateway {

    List<ReservationSummary> findFutureActive(UUID areaId);

    int cancelAll(UUID areaId, String justification, UUID actorId);
}
