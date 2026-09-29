package br.com.reservas.area.infra;

import br.com.reservas.area.domain.AreaInspection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaInspectionRepository extends JpaRepository<AreaInspection, UUID> {

    List<AreaInspection> findByAreaIdOrderByInspectedAtDesc(UUID areaId);

    boolean existsByIdAndAreaId(UUID id, UUID areaId);
}
