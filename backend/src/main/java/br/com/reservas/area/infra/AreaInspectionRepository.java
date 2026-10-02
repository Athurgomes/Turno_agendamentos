package br.com.reservas.area.infra;

import br.com.reservas.area.domain.AreaInspection;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AreaInspectionRepository extends JpaRepository<AreaInspection, UUID> {

    List<AreaInspection> findByAreaIdOrderByInspectedAtDesc(UUID areaId);

    boolean existsByIdAndAreaId(UUID id, UUID areaId);

    // GET /dashboard/home (F7-1, RF-SIN-01): última vistoria por área, em lote (evita N+1).
    @Query("select i.areaId as areaId, max(i.inspectedAt) as lastInspectedAt from AreaInspection i "
        + "where i.areaId in :areaIds group by i.areaId")
    List<LastInspectionProjection> findLastInspectedAtByAreaIdIn(@Param("areaIds") Collection<UUID> areaIds);

    interface LastInspectionProjection {
        UUID getAreaId();

        LocalDate getLastInspectedAt();
    }
}
