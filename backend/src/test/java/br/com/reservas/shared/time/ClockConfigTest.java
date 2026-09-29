package br.com.reservas.shared.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** D-32/FD-3: relogio simulado do perfil demo (offset fixo sobre o tempo real). */
class ClockConfigTest {

    private final ClockConfig config = new ClockConfig();

    @Test
    @DisplayName("D-32: no perfil demo, com APP_DEMO_NOW definido, o relogio comeca no instante configurado")
    void demoClockStartsAtConfiguredInstant() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");

        Clock clock = config.clock(env, "2026-11-10T10:00", "America/Sao_Paulo");

        assertThat(clock.instant()).isCloseTo(Instant.parse("2026-11-10T13:00:00Z"), org.assertj.core.api.Assertions
            .within(Duration.ofSeconds(2)));
    }

    @Test
    @DisplayName("D-32: o relogio simulado avanca em tempo real (nao fica parado)")
    void demoClockAdvancesWithRealTime() throws InterruptedException {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");

        Clock clock = config.clock(env, "2026-11-10T10:00", "America/Sao_Paulo");
        Instant first = clock.instant();
        Thread.sleep(20);
        Instant second = clock.instant();

        assertThat(second).isAfter(first);
    }

    @Test
    @DisplayName("D-32: APP_DEMO_NOW invalido falha a subida com mensagem clara")
    void invalidDemoNowFailsStartup() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");

        assertThatThrownBy(() -> config.clock(env, "nao-e-uma-data", "America/Sao_Paulo"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_DEMO_NOW");
    }

    @Test
    @DisplayName("D-32: fora do perfil demo, APP_DEMO_NOW e ignorado (relogio real)")
    void ignoresDemoNowOutsideDemoProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");

        Clock clock = config.clock(env, "2026-11-10T10:00", "America/Sao_Paulo");

        assertThat(clock.instant()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(Duration.ofSeconds(2)));
        assertThat(config.simulatedClockState(env, "2026-11-10T10:00", "America/Sao_Paulo").active()).isFalse();
    }

    @Test
    @DisplayName("D-32: sem APP_DEMO_NOW, o estado simulado fica inativo mesmo no perfil demo")
    void simulatedStateInactiveWithoutDemoNow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");

        assertThat(config.simulatedClockState(env, "", "America/Sao_Paulo").active()).isFalse();
    }

    @Test
    @DisplayName("D-32: com APP_DEMO_NOW e perfil demo, o estado simulado fica ativo")
    void simulatedStateActiveWithDemoNow() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");

        assertThat(config.simulatedClockState(env, "2026-11-10T10:00", "America/Sao_Paulo").active()).isTrue();
    }
}
