package br.com.reservas.unit.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** `POST /units` (docs/03): `{ block?, number, primary, members[] }`. */
public record CreateUnitRequest(
    String block,
    @NotBlank(message = "Número é obrigatório.") String number,
    @NotNull(message = "Morador principal é obrigatório.") @Valid ResidentRequest primary,
    @Valid List<ResidentRequest> members) {

    public List<ResidentRequest> membersOrEmpty() {
        return members == null ? List.of() : members;
    }
}
