package br.com.reservas.area.infra;

import br.com.reservas.area.domain.OpeningHours;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OpeningHoursRepository extends JpaRepository<OpeningHours, UUID> {

    List<OpeningHours> findByAreaIdOrderByDayOfWeek(UUID areaId);

    void deleteByAreaId(UUID areaId);
}
