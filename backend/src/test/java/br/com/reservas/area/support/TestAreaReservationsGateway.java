package br.com.reservas.area.support;

import br.com.reservas.area.application.AreaReservationsGateway;
import br.com.reservas.shared.reservation.ReservationSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Substitui {@code NoAreaReservationsGateway} nos testes que precisam
 * simular reservas futuras afetadas (RN-16), ate a F4 trazer a implementacao
 * real. {@link #setFuture(List)} controla o que {@link #findFutureActive}
 * devolve; resetar em {@code @BeforeEach} porque o bean e um singleton
 * compartilhado pelo contexto de teste.
 */
public class TestAreaReservationsGateway implements AreaReservationsGateway {

    private volatile List<ReservationSummary> future = List.of();
    private volatile int lastCancelCount;

    public void setFuture(List<ReservationSummary> future) {
        this.future = future;
    }

    public int lastCancelCount() {
        return lastCancelCount;
    }

    @Override
    public List<ReservationSummary> findFutureActive(UUID areaId) {
        return future;
    }

    @Override
    public int cancelAll(UUID areaId, String justification, UUID actorId) {
        lastCancelCount = future.size();
        future = List.of();
        return lastCancelCount;
    }

    @TestConfiguration
    public static class Config {

        @Bean
        @Primary
        public TestAreaReservationsGateway testAreaReservationsGateway() {
            return new TestAreaReservationsGateway();
        }
    }
}
