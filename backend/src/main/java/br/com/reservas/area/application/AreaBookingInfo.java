package br.com.reservas.area.application;

import br.com.reservas.area.domain.AreaStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Interface publica de leitura para a F4 (`reservation`): o que uma reserva
 * precisa saber sobre a area no momento de validar/criar (RN-18..25).
 * Deliberadamente pequeno: so os campos usados pelo motor de regras.
 */
public record AreaBookingInfo(UUID id, String name, AreaStatus status, int capacity, boolean requiresPayment,
    BigDecimal price, String paymentWhatsapp, List<OpeningHoursRange> openingHours) {

    public Optional<OpeningHoursRange> hoursForDay(int dayOfWeek) {
        return openingHours.stream().filter(h -> h.dayOfWeek() == dayOfWeek).findFirst();
    }
}
