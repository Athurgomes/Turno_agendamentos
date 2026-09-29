package br.com.reservas.reservation.support;

import br.com.reservas.area.support.AreaTestStorageConfig;
import br.com.reservas.auth.support.MutableClock;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Config compartilhada pelos testes de integração de `reservation`: um único
 * contexto Spring em cache para todos (cada contexto distinto abre o próprio
 * pool Hikari e, somados, estouram o `max_connections` do Postgres do
 * Testcontainers).
 *
 * <p>"Agora" = terça 2026-11-10 10:00 em America/Sao_Paulo (UTC-3): uma data
 * fixa qualquer, sem relação com a data real do sistema. Isso só é seguro
 * porque o {@code JwtDecoder} valida `exp`/`iat` com este mesmo {@link Clock}
 * (injetado como {@code @Primary}), não com o relógio do sistema.
 */
@TestConfiguration
@Import(AreaTestStorageConfig.class)
public class ReservationTestConfig {

    public static final Instant START = Instant.parse("2026-11-10T13:00:00Z");

    @Bean
    @Primary
    Clock reservationTestClock() {
        return new MutableClock(START, ZoneOffset.UTC);
    }
}
