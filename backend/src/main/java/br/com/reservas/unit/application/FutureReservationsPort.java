package br.com.reservas.unit.application;

import br.com.reservas.shared.reservation.ReservationSummary;
import java.util.List;
import java.util.UUID;

/**
 * P-11/RN-10: porta para "reservas futuras ativas da unidade", no mesmo
 * espirito da porta de area (D-44). O modulo `unit` nao pode depender do
 * modulo `reservation` para nao criar dependencia circular; implementada por
 * {@code reservation.infra.FutureReservationsPortImpl} (F4).
 *
 * <p>{@code unitIdentifier} vem do chamador (que já tem a {@code Unit} em
 * mãos) de propósito: evita o adaptador precisar depender de volta de
 * {@code UnitService} só para resolver o identificador da própria unidade
 * consultada, o que criaria um ciclo de beans Spring.
 */
public interface FutureReservationsPort {

    List<ReservationSummary> upcomingActiveByUnit(UUID unitId, String unitIdentifier);
}
