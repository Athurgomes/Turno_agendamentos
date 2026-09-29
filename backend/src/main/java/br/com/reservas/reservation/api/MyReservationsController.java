package br.com.reservas.reservation.api;

import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.reservation.application.ReservationService;
import br.com.reservas.reservation.application.ReservationView;
import br.com.reservas.shared.web.PageDto;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/me/reservations` (RF-RES-05, UNIT). O {@code unitId} sempre vem do token
 * (RN-01), nunca de query/corpo — proteção contra IDOR entre unidades.
 */
@RestController
@RequestMapping("/api/v1/me/reservations")
@PreAuthorize("hasRole('UNIT')")
public class MyReservationsController {

    private final ReservationService reservationService;
    private final CurrentUserProvider currentUserProvider;

    public MyReservationsController(ReservationService reservationService, CurrentUserProvider currentUserProvider) {
        this.reservationService = reservationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageDto<ReservationDto> list(@RequestParam(defaultValue = "upcoming") String scope, Pageable pageable) {
        UUID unitId = currentUserProvider.current().unitId();
        Page<ReservationView> page = reservationService.listMine(unitId, scope, pageable);
        return PageDto.of(page, ReservationDto::of);
    }

    // RN-30: própria unidade, PENDING_PAYMENT/CONFIRMED, até o prazo antes do início.
    @PostMapping("/{id}/cancel")
    public ReservationDto cancel(@PathVariable UUID id) {
        CurrentUser user = currentUserProvider.current();
        ReservationView view = reservationService.cancelByResident(user.unitId(), id, user.id());
        return ReservationDto.of(view);
    }
}
