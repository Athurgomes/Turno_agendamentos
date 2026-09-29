package br.com.reservas.unit.api;

import br.com.reservas.auth.application.AccountService;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.shared.web.PageDto;
import br.com.reservas.unit.application.CreateUnitResult;
import br.com.reservas.unit.application.TransferResult;
import br.com.reservas.unit.application.UnitService;
import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** `/units` (ADMIN, docs/03 "Unidades e moradores"). Controller fino: regra em {@link UnitService}. */
@RestController
@RequestMapping("/api/v1/units")
@PreAuthorize("hasRole('ADMIN')")
public class UnitController {

    private final UnitService unitService;
    private final AccountService accountService;
    private final CurrentUserProvider currentUserProvider;

    public UnitController(UnitService unitService, AccountService accountService,
        CurrentUserProvider currentUserProvider) {
        this.unitService = unitService;
        this.accountService = accountService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUnitResponse create(@Valid @RequestBody CreateUnitRequest request) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        UUID actorId = currentUserProvider.current().id();

        List<br.com.reservas.unit.application.ResidentInput> inputs = new java.util.ArrayList<>();
        inputs.add(request.primary().toInput(true));
        request.membersOrEmpty().forEach(member -> inputs.add(member.toInput(false)));

        CreateUnitResult result = unitService.create(condominiumId, actorId, request.block(), request.number(),
            inputs);
        UnitDetailDto unitDto = UnitDetailDto.of(result.unit(), result.credentials().username(), result.residents(),
            false);
        return new CreateUnitResponse(unitDto, CredentialsDto.from(result.credentials()));
    }

    @GetMapping
    public PageDto<UnitSummaryDto> search(@RequestParam(required = false) String search,
        @RequestParam(required = false) String block, Pageable pageable) {
        UUID condominiumId = currentUserProvider.current().condominiumId();
        Page<Unit> page = unitService.search(condominiumId, search, block, pageable);
        Map<UUID, List<Resident>> residentsByUnit = unitService.residentsByUnits(
            page.getContent().stream().map(Unit::getId).toList());
        return PageDto.of(page, unit -> UnitSummaryDto.of(unit, residentsByUnit.getOrDefault(unit.getId(), List.of())));
    }

    @GetMapping("/{id}")
    public UnitDetailDto detail(@PathVariable UUID id) {
        Unit unit = unitService.findActiveUnitOrThrow(id);
        List<Resident> residents = unitService.activeResidents(id);
        String username = accountService.usernameByUnit(id).orElse(unit.getIdentifier());
        return UnitDetailDto.of(unit, username, residents, false);
    }

    @PutMapping("/{id}")
    public UnitDetailDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUnitRequest request) {
        UUID actorId = currentUserProvider.current().id();
        List<Resident> residents = unitService.replaceResidents(id, actorId, request.toInputs());
        Unit unit = unitService.findActiveUnitOrThrow(id);
        String username = accountService.usernameByUnit(id).orElse(unit.getIdentifier());
        return UnitDetailDto.of(unit, username, residents, false);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) {
        UUID actorId = currentUserProvider.current().id();
        unitService.deactivate(id, actorId);
    }

    @PostMapping("/{id}/reset-password")
    public CredentialsDto resetPassword(@PathVariable UUID id) {
        UUID actorId = currentUserProvider.current().id();
        return CredentialsDto.from(unitService.resetPassword(id, actorId));
    }

    @PostMapping("/{id}/transfer")
    public TransferUnitResponse transfer(@PathVariable UUID id, @Valid @RequestBody TransferUnitRequest request) {
        UUID actorId = currentUserProvider.current().id();
        List<br.com.reservas.unit.application.ResidentInput> inputs = new java.util.ArrayList<>();
        inputs.add(request.primary().toInput(true));
        request.membersOrEmpty().forEach(member -> inputs.add(member.toInput(false)));

        TransferResult result = unitService.transfer(id, actorId, inputs);
        List<ReservationSummaryDto> affected = result.affectedReservations().stream()
            .map(ReservationSummaryDto::from)
            .toList();
        return new TransferUnitResponse(CredentialsDto.from(result.credentials()), affected);
    }
}
