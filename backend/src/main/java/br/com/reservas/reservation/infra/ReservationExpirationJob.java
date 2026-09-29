package br.com.reservas.reservation.infra;

import br.com.reservas.reservation.application.ReservationService;
import java.time.Clock;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * F5-2/RN-31: gancho periódico da expiração em lote (o gancho sob demanda de
 * cada consulta, em {@link ReservationService#expirePendingIfNeeded}, já
 * cobre o caso comum; este job só evita uma pendente vencida ficar visível
 * por muito tempo sem ninguém consultar aquela reserva entre o vencimento e a
 * próxima consulta). Desligado por padrão nos testes de integração
 * (`app.reservations.expiration-job-enabled=false` em
 * {@code AbstractIntegrationTest}) para não competir com o {@link Clock}
 * fixo/mutável dos testes: eles chamam {@link ReservationService#expirePendingIfNeeded}
 * direto, sem depender do agendador.
 */
@Component
@ConditionalOnProperty(prefix = "app.reservations", name = "expiration-job-enabled", havingValue = "true",
    matchIfMissing = true)
public class ReservationExpirationJob {

    private final ReservationService reservationService;
    private final Clock clock;

    public ReservationExpirationJob(ReservationService reservationService, Clock clock) {
        this.reservationService = reservationService;
        this.clock = clock;
    }

    @Scheduled(fixedRateString = "${app.reservations.expiration-interval:PT15M}")
    public void run() {
        reservationService.expirePendingIfNeeded(Instant.now(clock));
    }
}
