package br.com.reservas.auth.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * RNF-02: 10 requisicoes/minuto por IP em `/auth/login`. Janela fixa (nao
 * deslizante) em memoria, mantida por instancia (D-42: uma instancia no MVP).
 *
 * ponytail: mapa nunca remove IPs antigos (cresce com o numero de IPs
 * distintos vistos); aceitavel na escala do MVP/demo, adicionar expiracao
 * (ex.: Caffeine com TTL) se isso virar problema de memoria em producao.
 */
@Component
public class LoginRateLimiter {

    private static final int LIMIT_PER_MINUTE = 10;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginRateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** @return {@code true} se a requisicao pode prosseguir; {@code false} se estourou o limite. */
    public boolean tryAcquire(String clientIp) {
        Instant now = Instant.now(clock);
        Window window = windows.compute(clientIp, (ip, existing) -> {
            if (existing == null || Duration.between(existing.start, now).compareTo(WINDOW) >= 0) {
                return new Window(now, new AtomicInteger(1));
            }
            existing.count.incrementAndGet();
            return existing;
        });
        return window.count.get() <= LIMIT_PER_MINUTE;
    }

    private record Window(Instant start, AtomicInteger count) {
    }
}
