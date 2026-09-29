package br.com.reservas.settings.infra;

import br.com.reservas.settings.domain.CondominiumSettings;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CondominiumSettingsRepository extends JpaRepository<CondominiumSettings, UUID> {
}
