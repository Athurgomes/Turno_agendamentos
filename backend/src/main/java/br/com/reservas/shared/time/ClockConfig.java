package br.com.reservas.shared.time;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * RNF-10: relógio único injetável em toda a aplicação (nunca chamar
 * {@code now()} direto).
 *
 * <p>Fora do perfil {@code demo}, sempre {@link Clock#systemUTC()}
 * ({@code APP_DEMO_NOW} é ignorado, com um aviso no log se estiver definido
 * por engano). No perfil {@code demo}, se {@code APP_DEMO_NOW} estiver
 * definido (D-32, formato {@code yyyy-MM-dd'T'HH:mm}, interpretado no fuso do
 * condomínio), o relógio começa nesse instante local e avança em tempo real:
 * um offset fixo sobre {@code Clock.systemUTC()}, calculado uma única vez na
 * subida. Um {@code APP_DEMO_NOW} inválido falha a subida da aplicação.
 */
@Configuration
public class ClockConfig {

    private static final Logger log = LoggerFactory.getLogger(ClockConfig.class);

    @Bean
    Clock clock(Environment environment, @Value("${app.demo.now:}") String demoNow,
        @Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        return resolve(environment, demoNow, timezone).clock();
    }

    @Bean
    SimulatedClockState simulatedClockState(Environment environment, @Value("${app.demo.now:}") String demoNow,
        @Value("${app.timezone:America/Sao_Paulo}") String timezone) {
        return new SimulatedClockState(resolve(environment, demoNow, timezone).simulated());
    }

    private ResolvedClock resolve(Environment environment, String demoNow, String timezone) {
        if (!StringUtils.hasText(demoNow)) {
            return new ResolvedClock(Clock.systemUTC(), false);
        }

        boolean demoProfile = environment.acceptsProfiles(Profiles.of("demo"));
        if (!demoProfile) {
            log.warn("APP_DEMO_NOW definido fora do perfil demo; será ignorado.");
            return new ResolvedClock(Clock.systemUTC(), false);
        }

        Instant simulatedInstant = parseSimulatedInstant(demoNow, timezone);
        Clock simulated = Clock.offset(Clock.systemUTC(), Duration.between(Instant.now(), simulatedInstant));
        log.info("Relógio simulado ativo: {}", demoNow);
        return new ResolvedClock(simulated, true);
    }

    private Instant parseSimulatedInstant(String demoNow, String timezone) {
        try {
            return LocalDateTime.parse(demoNow).atZone(ZoneId.of(timezone)).toInstant();
        } catch (DateTimeException e) {
            throw new IllegalStateException(
                "APP_DEMO_NOW inválido ('" + demoNow + "'); use o formato yyyy-MM-dd'T'HH:mm (D-32).", e);
        }
    }

    private record ResolvedClock(Clock clock, boolean simulated) {
    }
}
