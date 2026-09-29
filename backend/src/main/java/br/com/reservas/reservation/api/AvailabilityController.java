package br.com.reservas.reservation.api;

import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.reservation.application.AvailabilityService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * `GET /areas/{id}/availability` (docs/03, F4-4; "todos"). Vive no módulo
 * `reservation` (não em `area`, D-44/codebase-design): é quem sabe combinar
 * horário de funcionamento + {@link br.com.reservas.reservation.domain.ReservationPolicy}
 * + as próprias reservas/bloqueios; `area` continua só expondo
 * {@code AreaQueryService.findBookable}, sem precisar de método novo.
 */
@RestController
@RequestMapping("/api/v1/areas/{id}/availability")
public class AvailabilityController {

    private final AvailabilityService availabilityService;
    private final CurrentUserProvider currentUserProvider;

    public AvailabilityController(AvailabilityService availabilityService, CurrentUserProvider currentUserProvider) {
        this.availabilityService = availabilityService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<AvailabilityDayDto> availability(@PathVariable UUID id,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        // RN-16/vibe-security: UNIT não recebe dado de outra unidade em `busy` (reservationId,
        // code, unitIdentifier, status ficam null); só S/A veem esses campos administrativos.
        boolean includeAdminFields = currentUserProvider.current().role() != Role.UNIT;
        return availabilityService.forArea(id, from, to).stream()
            .map(day -> AvailabilityDayDto.of(day, includeAdminFields))
            .toList();
    }
}
