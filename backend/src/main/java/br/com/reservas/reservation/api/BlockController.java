package br.com.reservas.reservation.api;

import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.reservation.application.AdminReservationView;
import br.com.reservas.reservation.application.ReservationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/blocks` (S/A, docs/03 "Reservas e bloqueios", RF-RES-10/RN-33): eventos do
 * condomínio (assembleia, manutenção pontual) que ocupam a agenda de uma área
 * sem morador nem cobrança. Controller fino: regra em {@link ReservationService}.
 */
@RestController
@RequestMapping("/api/v1/blocks")
@PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
public class BlockController {

    private final ReservationService reservationService;
    private final CurrentUserProvider currentUserProvider;

    public BlockController(ReservationService reservationService, CurrentUserProvider currentUserProvider) {
        this.reservationService = reservationService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminReservationDto create(@Valid @RequestBody CreateBlockRequest request) {
        CurrentUser user = currentUserProvider.current();
        AdminReservationView view = reservationService.createBlock(user.condominiumId(), user.id(),
            user.role().name(), request.toCommand());
        return AdminReservationDto.of(view);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        CurrentUser user = currentUserProvider.current();
        reservationService.cancelBlock(id, user.id(), user.role().name());
    }
}
