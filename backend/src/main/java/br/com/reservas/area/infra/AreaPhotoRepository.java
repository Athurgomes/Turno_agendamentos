package br.com.reservas.area.infra;

import br.com.reservas.area.domain.AreaPhoto;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaPhotoRepository extends JpaRepository<AreaPhoto, UUID> {

    Optional<AreaPhoto> findByIdAndAreaId(UUID id, UUID areaId);

    List<AreaPhoto> findByAreaIdAndFeaturedTrueAndArchivedFalseOrderByTakenAtDesc(UUID areaId);

    /** Foto de vitrine mais recente (catalogo, `coverPhotoUrl`). */
    Optional<AreaPhoto> findFirstByAreaIdAndFeaturedTrueAndArchivedFalseOrderByTakenAtDescCreatedAtDesc(UUID areaId);

    List<AreaPhoto> findByAreaIdAndArchivedFalseAndTakenAtBetweenOrderByTakenAtDesc(UUID areaId, LocalDate from,
        LocalDate to);

    List<AreaPhoto> findByAreaIdAndTakenAtBetweenOrderByTakenAtDesc(UUID areaId, LocalDate from, LocalDate to);
}
