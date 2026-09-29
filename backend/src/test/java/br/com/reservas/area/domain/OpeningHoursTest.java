package br.com.reservas.area.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.shared.error.BusinessException;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** D-44: horario de funcionamento (multiplos de 30 min, fechamento apos abertura). */
class OpeningHoursTest {

    private final UUID areaId = UUID.randomUUID();

    @Test
    @DisplayName("D-44: horario com minuto fora de 00/30 e rejeitado")
    void rejectsNonHalfHourStep() {
        assertThatThrownBy(() -> new OpeningHours(areaId, 1, LocalTime.of(10, 15), LocalTime.of(12, 0)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("D-44: fechamento igual ou antes da abertura e rejeitado")
    void rejectsCloseNotAfterOpen() {
        assertThatThrownBy(() -> new OpeningHours(areaId, 1, LocalTime.of(12, 0), LocalTime.of(12, 0)))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("D-44: dia da semana fora de 1..7 e rejeitado")
    void rejectsDayOfWeekOutOfRange() {
        assertThatThrownBy(() -> new OpeningHours(areaId, 8, LocalTime.of(10, 0), LocalTime.of(12, 0)))
            .isInstanceOf(BusinessException.class);
    }
}
