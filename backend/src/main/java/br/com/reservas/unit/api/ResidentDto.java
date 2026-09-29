package br.com.reservas.unit.api;

import br.com.reservas.unit.domain.Cpf;
import br.com.reservas.unit.domain.Resident;
import java.util.UUID;

/** `ResidentDto` (docs/03): `cpf` completo so para ADMIN; mascarado em `/me/unit` (RNF-01). */
public record ResidentDto(UUID id, String name, String phone, String email, String cpf, boolean primary) {

    public static ResidentDto full(Resident resident) {
        return new ResidentDto(resident.getId(), resident.getName(), resident.getPhone(), resident.getEmail(),
            resident.getCpf(), resident.isPrimary());
    }

    public static ResidentDto masked(Resident resident) {
        return new ResidentDto(resident.getId(), resident.getName(), resident.getPhone(), resident.getEmail(),
            Cpf.mask(resident.getCpf()), resident.isPrimary());
    }
}
