package br.com.reservas.area.application;

import br.com.reservas.area.domain.Area;
import br.com.reservas.area.infra.AreaRepository;
import br.com.reservas.area.infra.OpeningHoursRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Interface publica que a F4 (`reservation`) usa para validar/criar reservas
 * (RN-18..25): {@link #findBookable(UUID)} devolve tudo que o motor de regras
 * precisa saber sobre a area em uma unica chamada. Mantido pequeno de
 * proposito (D-44/codebase-design): um unico metodo.
 */
@Service
public class AreaQueryService {

    private final AreaRepository areas;
    private final OpeningHoursRepository openingHours;

    public AreaQueryService(AreaRepository areas, OpeningHoursRepository openingHours) {
        this.areas = areas;
        this.openingHours = openingHours;
    }

    @Transactional(readOnly = true)
    public Optional<AreaBookingInfo> findBookable(UUID areaId) {
        return areas.findByIdAndDeletedAtIsNull(areaId).map(this::toBookingInfo);
    }

    /**
     * F4: dados de exibição (nome, cobrança) de várias áreas de uma vez, para
     * listagens de reservas (evita N+1). Sem horário de funcionamento — só a
     * validação de uma reserva específica (única, via {@link #findBookable})
     * precisa disso.
     */
    @Transactional(readOnly = true)
    public Map<UUID, AreaBookingInfo> findBookableByIds(Collection<UUID> areaIds) {
        if (areaIds.isEmpty()) {
            return Map.of();
        }
        return areas.findByIdInAndDeletedAtIsNull(areaIds).stream()
            .collect(Collectors.toMap(Area::getId, area -> toBookingInfo(area, List.of())));
    }

    private AreaBookingInfo toBookingInfo(Area area) {
        var hours = openingHours.findByAreaIdOrderByDayOfWeek(area.getId()).stream()
            .map(h -> new OpeningHoursRange(h.getDayOfWeek(), h.getOpenTime(), h.getCloseTime()))
            .toList();
        return toBookingInfo(area, hours);
    }

    private AreaBookingInfo toBookingInfo(Area area, List<OpeningHoursRange> hours) {
        return new AreaBookingInfo(area.getId(), area.getName(), area.getStatus(), area.getCapacity(),
            area.isRequiresPayment(), area.getPrice(), area.getPaymentWhatsapp(), hours);
    }
}
