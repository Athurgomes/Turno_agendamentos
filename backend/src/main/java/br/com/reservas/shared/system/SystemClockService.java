package br.com.reservas.shared.system;

import br.com.reservas.shared.condominium.CondominiumLookup;
import br.com.reservas.shared.time.SimulatedClockState;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

/**
 * RNF-10: instante atual do {@link Clock} da aplicacao (UTC, ou o relogio
 * simulado da FD-3/D-32 no perfil demo) e o fuso do condominio.
 */
@Service
public class SystemClockService {

    private final Clock clock;
    private final CondominiumLookup condominiums;
    private final SimulatedClockState simulatedClockState;

    public SystemClockService(Clock clock, CondominiumLookup condominiums, SimulatedClockState simulatedClockState) {
        this.clock = clock;
        this.condominiums = condominiums;
        this.simulatedClockState = simulatedClockState;
    }

    public ClockResponse currentClock() {
        return new ClockResponse(Instant.now(clock), condominiums.currentTimezone(), simulatedClockState.active());
    }
}
