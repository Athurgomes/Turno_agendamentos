package br.com.reservas.unit.infra;

import br.com.reservas.unit.domain.Resident;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResidentRepository extends JpaRepository<Resident, UUID> {

    List<Resident> findByUnitIdAndDeletedAtIsNull(UUID unitId);

    List<Resident> findByUnitIdInAndDeletedAtIsNull(List<UUID> unitIds);

    List<Resident> findByIdIn(Collection<UUID> ids);

    Optional<Resident> findByIdAndUnitIdAndDeletedAtIsNull(UUID id, UUID unitId);

    boolean existsByUnitIdAndCpfAndDeletedAtIsNull(UUID unitId, String cpf);
}
