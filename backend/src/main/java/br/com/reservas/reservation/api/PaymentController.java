package br.com.reservas.reservation.api;

import br.com.reservas.auth.application.CurrentUser;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.reservation.application.AdminReservationView;
import br.com.reservas.reservation.application.PendingPaymentView;
import br.com.reservas.reservation.application.ReservationService;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/payments/pending` e `/reservations/{id}/confirm-payment` (ADMIN, docs/03
 * "Reservas e bloqueios", RF-PAG-02..04, D-49): aba "Confirmações" do
 * pagamento manual (D-14: nenhum gateway processa pagamento no MVP).
 * Controller fino: regra em {@link ReservationService}.
 */
@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
public class PaymentController {

    private final ReservationService reservationService;
    private final CurrentUserProvider currentUserProvider;

    public PaymentController(ReservationService reservationService, CurrentUserProvider currentUserProvider) {
        this.reservationService = reservationService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/payments/pending")
    public List<PendingPaymentDto> pending() {
        List<PendingPaymentView> found = reservationService.pendingPayments();
        return found.stream().map(PendingPaymentDto::of).toList();
    }

    @PostMapping("/reservations/{id}/confirm-payment")
    public AdminReservationDto confirmPayment(@PathVariable UUID id) {
        CurrentUser user = currentUserProvider.current();
        AdminReservationView view = reservationService.confirmPayment(id, user.id());
        return AdminReservationDto.of(view);
    }
}
