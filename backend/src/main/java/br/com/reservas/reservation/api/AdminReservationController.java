package br.com.reservas.reservation.api;

import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.reservation.application.AdminReservationView;
import br.com.reservas.reservation.application.ReservationEventView;
import br.com.reservas.reservation.application.ReservationFilter;
import br.com.reservas.reservation.application.ReservationService;
import br.com.reservas.reservation.domain.ReservationKind;
import br.com.reservas.reservation.domain.ReservationStatus;
import br.com.reservas.shared.web.PageDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/reservations` (S/A, docs/03 "Reservas e bloqueios"): agenda completa,
 * histórico, alteração e cancelamento pelo ADMIN. O detalhe (`GET /{id}`,
 * também usado pela conta UNIT) fica em {@link ReservationController} — a
 * mesma rota, dono diferente por perfil (D-16: aqui é sempre S/A ou ADMIN).
 */
@RestController
@RequestMapping("/api/v1/reservations")
@PreAuthorize("hasAnyRole('SYNDIC', 'ADMIN')")
public class AdminReservationController {

    private final ReservationService reservationService;
    private final CurrentUserProvider currentUserProvider;

    public AdminReservationController(ReservationService reservationService,
        CurrentUserProvider currentUserProvider) {
        this.reservationService = reservationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public PageDto<AdminReservationDto> search(@RequestParam(required = false) UUID areaId,
        @RequestParam(required = false) LocalDate from, @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) ReservationStatus status, @RequestParam(required = false) UUID unitId,
        @RequestParam(required = false) String unitIdentifier, @RequestParam(required = false) ReservationKind kind,
        @PageableDefault(sort = "startAt") Pageable pageable) {
        ReservationFilter filter = new ReservationFilter(areaId, from, to, status, unitId, unitIdentifier, kind);
        Page<AdminReservationView> page = reservationService.search(filter, pageable);
        return PageDto.of(page, AdminReservationDto::of);
    }

    @GetMapping("/{id}/events")
    public List<ReservationEventDto> events(@PathVariable UUID id) {
        List<ReservationEventView> found = reservationService.events(id);
        return found.stream().map(ReservationEventDto::of).toList();
    }

    // D-16: só ADMIN (SYNDIC autenticado -> 403, o método sobrescreve a classe).
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminReservationDto update(@PathVariable UUID id, @Valid @RequestBody UpdateReservationRequest request) {
        CurrentUser user = currentUserProvider.current();
        AdminReservationView view = reservationService.updateByAdmin(id, user.id(), request.toCommand());
        return AdminReservationDto.of(view);
    }

    // D-16: só ADMIN.
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    public AdminReservationDto cancel(@PathVariable UUID id, @RequestBody CancelReservationRequest request) {
        CurrentUser user = currentUserProvider.current();
        AdminReservationView view = reservationService.cancelByAdmin(id, user.id(), request.justification());
        return AdminReservationDto.of(view);
    }
}
