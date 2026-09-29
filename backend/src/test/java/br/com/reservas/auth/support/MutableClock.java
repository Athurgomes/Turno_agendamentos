package br.com.reservas.auth.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicReference;

/** Clock de teste que pode ser avancado manualmente (ex.: RN-05, janela de bloqueio de 15 min). */
public class MutableClock extends Clock {

    private final AtomicReference<Instant> instant;
    private final ZoneId zone;

    public MutableClock(Instant initial, ZoneId zone) {
        this.instant = new AtomicReference<>(initial);
        this.zone = zone;
    }

    public void advance(java.time.Duration duration) {
        instant.updateAndGet(current -> current.plus(duration));
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new MutableClock(instant.get(), zone);
    }

    @Override
    public Instant instant() {
        return instant.get();
    }
}
