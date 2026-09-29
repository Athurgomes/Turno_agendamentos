package br.com.reservas.area.api;

import br.com.reservas.area.domain.Area;
import java.math.BigDecimal;
import java.util.UUID;

/** `AreaSummary` (docs/03): catalogo, sem areas excluidas (soft delete). */
public record AreaSummaryDto(UUID id, String name, String category, String status, int capacity,
    boolean requiresPayment, BigDecimal price, String coverPhotoUrl) {

    public static AreaSummaryDto of(Area area, String coverPhotoUrl) {
        return new AreaSummaryDto(area.getId(), area.getName(), area.getCategory().name(), area.getStatus().name(),
            area.getCapacity(), area.isRequiresPayment(), area.getPrice(), coverPhotoUrl);
    }
}
