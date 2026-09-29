package br.com.reservas.unit.api;

import br.com.reservas.unit.application.ResidentInput;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

/** Item de `primary`/`members` em `POST /units` e `POST /units/{id}/transfer` (docs/03). */
public record ResidentRequest(
    @NotBlank(message = "Nome é obrigatório.") String name,
    @NotBlank(message = "Telefone é obrigatório.")
    @Pattern(regexp = "^[0-9]{12,13}$", message = "Telefone deve ter 12 ou 13 dígitos com DDI.") String phone,
    @Email(message = "E-mail inválido.") String email,
    String cpf) {

    public ResidentInput toInput(boolean primary) {
        return new ResidentInput(null, name, phone, email, cpf, primary);
    }

    public ResidentInput toInput(UUID id, boolean primary) {
        return new ResidentInput(id, name, phone, email, cpf, primary);
    }
}
