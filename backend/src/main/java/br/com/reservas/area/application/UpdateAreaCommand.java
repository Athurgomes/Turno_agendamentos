package br.com.reservas.area.application;

import br.com.reservas.area.domain.AreaCategory;
import java.math.BigDecimal;
import java.util.List;

/**
 * `PUT /areas/{id}` (D-44): atualizacao parcial, campo `null`/ausente =
 * inalterado. Campos restritos a ADMIN (D-20): `name`, `category`,
 * `requiresPayment`, `price`, `paymentWhatsapp`, `openingHours`. SYNDIC so
 * pode enviar `description`, `rules`, `conductGuidelines`, `capacity`.
 */
public record UpdateAreaCommand(String name, AreaCategory category, String description, String rules,
    String conductGuidelines, Integer capacity, Boolean requiresPayment, BigDecimal price, String paymentWhatsapp,
    List<OpeningHoursInput> openingHours, Integer version) {

    public boolean hasAdminOnlyField() {
        return name != null || category != null || requiresPayment != null || price != null
            || paymentWhatsapp != null || openingHours != null;
    }
}
