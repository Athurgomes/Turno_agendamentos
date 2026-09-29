package br.com.reservas.area.api;

import br.com.reservas.area.application.CreateAreaCommand;
import br.com.reservas.area.domain.AreaCategory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;

/** `data` (JSON) de `POST /areas` (docs/03, RN-11, RN-12). */
public record CreateAreaRequest(@NotBlank String name, @NotNull AreaCategory category, @NotBlank String description,
    @NotBlank String rules, @NotBlank String conductGuidelines, @Min(1) int capacity, boolean requiresPayment,
    BigDecimal price, String paymentWhatsapp, @NotEmpty List<@Valid OpeningHoursRequest> openingHours) {

    public CreateAreaCommand toCommand() {
        return new CreateAreaCommand(name, category, description, rules, conductGuidelines, capacity,
            requiresPayment, price, paymentWhatsapp,
            openingHours.stream().map(OpeningHoursRequest::toInput).toList());
    }
}
