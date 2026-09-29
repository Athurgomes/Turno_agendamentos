package br.com.reservas.settings.api;

import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.settings.application.SyndicService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** `/admin/syndics` (ADMIN, RF-UNI-07). */
@RestController
@RequestMapping("/api/v1/admin/syndics")
@PreAuthorize("hasRole('ADMIN')")
public class SyndicController {

    private final SyndicService syndicService;
    private final CurrentUserProvider currentUserProvider;

    public SyndicController(SyndicService syndicService, CurrentUserProvider currentUserProvider) {
        this.syndicService = syndicService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<SyndicDto> list() {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        return syndicService.list(condominiumId).stream().map(SyndicDto::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateSyndicResponse create(@Valid @RequestBody CreateSyndicRequest request) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        UUID actorId = currentUserProvider.current().id();
        SyndicService.Created created = syndicService.create(condominiumId, actorId, request.name(),
            request.email(), request.phone());
        UserAccount account = created.account();
        return new CreateSyndicResponse(SyndicDto.from(account),
            new CreateSyndicResponse.Credentials(account.getEmail(), created.tempPassword()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        syndicService.deactivate(id, currentUserProvider.current().id());
    }
}
