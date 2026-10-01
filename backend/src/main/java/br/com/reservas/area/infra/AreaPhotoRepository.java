package br.com.reservas.area.infra;

import br.com.reservas.area.domain.AreaPhoto;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AreaPhotoRepository extends JpaRepository<AreaPhoto, UUID> {

    Optional<AreaPhoto> findByIdAndAreaId(UUID id, UUID areaId);

    List<AreaPhoto> findByAreaIdAndFeaturedTrueAndArchivedFalseOrderByTakenAtDesc(UUID areaId);

    /** Foto de vitrine mais recente (catalogo, `coverPhotoUrl`). */
    Optional<AreaPhoto> findFirstByAreaIdAndFeaturedTrueAndArchivedFalseOrderByTakenAtDescCreatedAtDesc(UUID areaId);

    /**
     * F9-1/RNF-04: candidatas a foto de capa de varias areas de uma vez (uma
     * unica consulta para o catalogo inteiro, evita N+1 de {@link
     * #findFirstByAreaIdAndFeaturedTrueAndArchivedFalseOrderByTakenAtDescCreatedAtDesc}).
     * Ordenada por area e depois pela mesma prioridade de "mais recente": quem
     * monta o mapa fica so com a primeira ocorrencia de cada area.
     */
    List<AreaPhoto> findByAreaIdInAndFeaturedTrueAndArchivedFalseOrderByAreaIdAscTakenAtDescCreatedAtDesc(
        Collection<UUID> areaIds);

    List<AreaPhoto> findByAreaIdAndArchivedFalseAndTakenAtBetweenOrderByTakenAtDesc(UUID areaId, LocalDate from,
        LocalDate to);

    List<AreaPhoto> findByAreaIdAndTakenAtBetweenOrderByTakenAtDesc(UUID areaId, LocalDate from, LocalDate to);
}
