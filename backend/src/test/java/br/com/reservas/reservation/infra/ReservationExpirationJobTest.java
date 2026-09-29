package br.com.reservas.reservation.infra;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import br.com.reservas.reservation.application.ReservationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * F5-2: teste de unidade puro (sem Spring/DB, ver `codebase-design`/ponytail — o job é um
 * delegador de uma linha, não precisa de um contexto Spring inteiro/outro pool Hikari só pra
 * isso, D-37/AbstractIntegrationTest já avisam sobre `max_connections` com contextos demais). O
 * comportamento de RN-31 em si (horário exato, motivo, evento EXPIRED) é coberto em
 * {@code ReservationServiceExpirationTest} (contexto compartilhado com os demais testes de
 * `reservation`), chamando {@link ReservationService#expirePendingIfNeeded} diretamente.
 */
class ReservationExpirationJobTest {

    @Test
    @DisplayName("F5-2: o job chama ReservationService.expirePendingIfNeeded com o Instant do Clock injetado")
    void delegatesToReservationServiceWithClockInstant() {
        ReservationService reservationService = Mockito.mock(ReservationService.class);
        Instant now = Instant.parse("2026-11-11T17:00:00Z");
        Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
        ReservationExpirationJob job = new ReservationExpirationJob(reservationService, fixedClock);

        job.run();

        verify(reservationService, times(1)).expirePendingIfNeeded(now);
    }
}
