package br.com.reservas.unit.api;

import br.com.reservas.auth.application.AccountService;
import br.com.reservas.auth.application.CurrentUserProvider;
import br.com.reservas.unit.application.MyUnitService;
import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * `/me/unit` (UNIT, RF-UNI-05, RN-09). O {@code unitId} sempre vem do token
 * (RN-01), nunca de path/corpo — protecao contra IDOR entre unidades.
 */
@RestController
@RequestMapping("/api/v1/me/unit")
@PreAuthorize("hasRole('UNIT')")
public class MyUnitController {

    private final MyUnitService myUnitService;
    private final AccountService accountService;
    private final CurrentUserProvider currentUserProvider;

    public MyUnitController(MyUnitService myUnitService, AccountService accountService,
        CurrentUserProvider currentUserProvider) {
        this.myUnitService = myUnitService;
        this.accountService = accountService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public UnitDetailDto get() {
        UUID unitId = currentUnitId();
        Unit unit = myUnitService.getUnit(unitId);
        List<Resident> residents = myUnitService.activeResidents(unitId);
        String username = accountService.usernameByUnit(unitId).orElse(unit.getIdentifier());
        return UnitDetailDto.of(unit, username, residents, true);
    }

    @PostMapping("/residents")
    @ResponseStatus(HttpStatus.CREATED)
    public ResidentDto addResident(@Valid @RequestBody AddResidentRequest request) {
        Resident resident = myUnitService.addResident(currentUnitId(), request.name(), request.phone(),
            request.email(), request.cpf());
        return ResidentDto.masked(resident);
    }

    @PutMapping("/residents/{id}")
    public ResidentDto updateResident(@PathVariable UUID id, @Valid @RequestBody UpdateResidentRequest request) {
        Resident resident = myUnitService.updateResident(currentUnitId(), id, request.name(), request.phone(),
            request.email());
        return ResidentDto.masked(resident);
    }

    @DeleteMapping("/residents/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeResident(@PathVariable UUID id) {
        myUnitService.removeResident(currentUnitId(), id);
    }

    private UUID currentUnitId() {
        return currentUserProvider.current().unitId();
    }
}
