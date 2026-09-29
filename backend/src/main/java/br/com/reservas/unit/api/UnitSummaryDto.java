package br.com.reservas.unit.api;

import br.com.reservas.unit.domain.Resident;
import br.com.reservas.unit.domain.Unit;
import java.util.List;
import java.util.UUID;

/** `UnitSummary` (docs/03): item de `GET /units`. */
public record UnitSummaryDto(UUID id, String block, String number, String identifier, boolean active,
    PrimaryResidentDto primaryResident, int residentsCount) {

    public record PrimaryResidentDto(String name, String phone) {
    }

    public static UnitSummaryDto of(Unit unit, List<Resident> residents) {
        PrimaryResidentDto primary = residents.stream()
            .filter(Resident::isPrimary)
            .findFirst()
            .map(r -> new PrimaryResidentDto(r.getName(), r.getPhone()))
            .orElse(null);
        return new UnitSummaryDto(unit.getId(), unit.getBlock(), unit.getNumber(), unit.getIdentifier(),
            unit.isActive(), primary, residents.size());
    }
}
