package br.com.reservas.reservation.infra;

import br.com.reservas.reservation.domain.ReservationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationEventRepository extends JpaRepository<ReservationEvent, UUID> {

    // GET /reservations/{id}/events (RF-RES-09): em ordem cronológica.
    List<ReservationEvent> findByReservationIdOrderByOccurredAtAsc(UUID reservationId);
}
