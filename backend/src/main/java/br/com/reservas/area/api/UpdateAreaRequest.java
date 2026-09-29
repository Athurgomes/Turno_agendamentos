package br.com.reservas.area.api;

import br.com.reservas.area.application.OpeningHoursInput;
import br.com.reservas.area.application.UpdateAreaCommand;
import br.com.reservas.area.domain.AreaCategory;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

/** `PUT /areas/{id}` (docs/03, D-44): parcial, `null`/ausente = inalterado. */
public record UpdateAreaRequest(String name, AreaCategory category, String description, String rules,
    String conductGuidelines, Integer capacity, Boolean requiresPayment, BigDecimal price, String paymentWhatsapp,
    List<@Valid OpeningHoursRequest> openingHours, Integer version) {

    public UpdateAreaCommand toCommand() {
        List<OpeningHoursInput> hours = openingHours == null ? null
            : openingHours.stream().map(OpeningHoursRequest::toInput).toList();
        return new UpdateAreaCommand(name, category, description, rules, conductGuidelines, capacity,
            requiresPayment, price, paymentWhatsapp, hours, version);
    }
}
