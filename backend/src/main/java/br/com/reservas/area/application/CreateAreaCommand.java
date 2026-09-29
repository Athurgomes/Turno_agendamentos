package br.com.reservas.area.application;

import br.com.reservas.area.domain.AreaCategory;
import java.math.BigDecimal;
import java.util.List;

public record CreateAreaCommand(String name, AreaCategory category, String description, String rules,
    String conductGuidelines, int capacity, boolean requiresPayment, BigDecimal price, String paymentWhatsapp,
    List<OpeningHoursInput> openingHours) {
}
