package br.com.reservas.settings.infra;

import br.com.reservas.settings.domain.Condominium;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondominiumRepository extends JpaRepository<Condominium, UUID> {
}
