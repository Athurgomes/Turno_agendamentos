package br.com.reservas.area.api;

import br.com.reservas.area.domain.Area;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * `AreaDetail` (docs/03): `photos` traz so as de vitrine nao arquivadas, para
 * todos os perfis; o historico completo e `GET /areas/{id}/photos`.
 */
public record AreaDetailDto(UUID id, String name, String category, String status, String description, String rules,
    String conductGuidelines, int capacity, boolean requiresPayment, BigDecimal price, String paymentWhatsapp,
    List<OpeningHoursDto> openingHours, List<PhotoDto> photos, int version) {

    public static AreaDetailDto of(Area area, List<OpeningHoursDto> openingHours, List<PhotoDto> photos) {
        return new AreaDetailDto(area.getId(), area.getName(), area.getCategory().name(), area.getStatus().name(),
            area.getDescription(), area.getRules(), area.getConductGuidelines(), area.getCapacity(),
            area.isRequiresPayment(), area.getPrice(), area.getPaymentWhatsapp(), openingHours, photos,
            area.getVersion());
    }
}
