package br.com.reservas.settings.api;

import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.settings.application.CondominiumSettingsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** `/admin/settings` (ADMIN) e `/settings/public` (qualquer perfil autenticado) — D-43. */
@RestController
public class SettingsController {

    private final CondominiumSettingsService settingsService;
    private final CurrentUserProvider currentUserProvider;

    public SettingsController(CondominiumSettingsService settingsService, CurrentUserProvider currentUserProvider) {
        this.settingsService = settingsService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/v1/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto get() {
        return SettingsDto.from(settingsService.get());
    }

    @PutMapping("/api/v1/admin/settings")
    @PreAuthorize("hasRole('ADMIN')")
    public SettingsDto update(@Valid @RequestBody UpdateSettingsRequest request) {
        return SettingsDto.from(settingsService.update(request.toCommand(), currentUserProvider.current().id()));
    }

    // Qualquer perfil autenticado (sem @PreAuthorize): SecurityConfig ja exige token valido em toda rota nao publica.
    @GetMapping("/api/v1/settings/public")
    public PublicSettingsDto getPublic() {
        return PublicSettingsDto.from(settingsService.get());
    }
}
