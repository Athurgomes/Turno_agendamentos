package br.com.reservas.unit.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** `POST /units/{id}/transfer` (docs/03, RN-10): igual ao cadastro, sem `block`/`number`. */
public record TransferUnitRequest(
    @NotNull(message = "Morador principal é obrigatório.") @Valid ResidentRequest primary,
    @Valid List<ResidentRequest> members) {

    public List<ResidentRequest> membersOrEmpty() {
        return members == null ? List.of() : members;
    }
}
