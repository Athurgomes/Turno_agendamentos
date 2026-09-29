package br.com.reservas.reservation.api;

import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.reservation.application.ReservationCreationResult;
import br.com.reservas.reservation.application.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** `/reservations` (docs/03 "Reservas e bloqueios"). Controller fino: regra em {@link ReservationService}. */
@RestController
@RequestMapping("/api/v1/reservations")
@PreAuthorize("hasRole('UNIT')")
public class ReservationController {

    private final ReservationService reservationService;
    private final CurrentUserProvider currentUserProvider;

    public ReservationController(ReservationService reservationService, CurrentUserProvider currentUserProvider) {
        this.reservationService = reservationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateReservationResponse create(@Valid @RequestBody CreateReservationRequest request) {
        CurrentUser user = currentUserProvider.current();
        ReservationCreationResult result = reservationService.create(user.unitId(), user.id(), request.toCommand());
        return new CreateReservationResponse(ReservationDto.of(result.reservation()), result.whatsappPaymentUrl());
    }

    // RN-01: método sobrescreve a classe (`hasRole('UNIT')`) porque a mesma rota também atende S/A
    // (docs/03); a checagem de dono (morador só a própria reserva) é feita no serviço, não aqui.
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('UNIT', 'SYNDIC', 'ADMIN')")
    public Object detail(@PathVariable UUID id) {
        CurrentUser user = currentUserProvider.current();
        if (user.role() == Role.UNIT) {
            return ReservationDto.of(reservationService.findMine(user.unitId(), id));
        }
        return AdminReservationDto.of(reservationService.findForAdmin(id));
    }
}
