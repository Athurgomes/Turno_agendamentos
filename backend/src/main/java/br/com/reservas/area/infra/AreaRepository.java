package br.com.reservas.area.infra;

import br.com.reservas.area.domain.Area;
import br.com.reservas.area.domain.AreaCategory;
import br.com.reservas.area.domain.AreaStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaRepository extends JpaRepository<Area, UUID> {

    Optional<Area> findByIdAndDeletedAtIsNull(UUID id);

    // Resolução em lote (evita N+1 em listagens de reservas com várias áreas).
    List<Area> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);

    List<Area> findByCondominiumIdAndDeletedAtIsNullAndCategoryAndStatus(UUID condominiumId, AreaCategory category,
        AreaStatus status);

    List<Area> findByCondominiumIdAndDeletedAtIsNullAndCategory(UUID condominiumId, AreaCategory category);

    List<Area> findByCondominiumIdAndDeletedAtIsNullAndStatus(UUID condominiumId, AreaStatus status);

    List<Area> findByCondominiumIdAndDeletedAtIsNull(UUID condominiumId);
}
