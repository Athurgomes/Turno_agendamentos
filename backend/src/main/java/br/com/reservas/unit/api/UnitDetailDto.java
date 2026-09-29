package br.com.reservas.unit.api;

import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import java.util.List;
import java.util.UUID;

/** `UnitDetail` (docs/03): usado por `GET /units/{id}` (CPF completo) e `GET /me/unit` (CPF mascarado). */
public record UnitDetailDto(UUID id, String block, String number, String identifier, String username,
    boolean active, List<ResidentDto> residents) {

    public static UnitDetailDto of(Unit unit, String username, List<Resident> residents, boolean maskCpf) {
        List<ResidentDto> dtos = residents.stream()
            .map(r -> maskCpf ? ResidentDto.masked(r) : ResidentDto.full(r))
            .toList();
        return new UnitDetailDto(unit.getId(), unit.getBlock(), unit.getNumber(), unit.getIdentifier(), username,
            unit.isActive(), dtos);
    }
}
