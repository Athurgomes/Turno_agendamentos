package br.com.reservas.unit.api;

import br.com.reservas.unit.application.ResidentInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

/** `PUT /units/{id}` (D-43): substitui a lista de moradores da unidade. */
public record UpdateUnitRequest(@NotEmpty(message = "Informe ao menos um morador.") @Valid List<ResidentItem> residents) {

    public record ResidentItem(
        UUID id,
        @NotBlank(message = "Nome é obrigatório.") String name,
        @NotBlank(message = "Telefone é obrigatório.")
        @Pattern(regexp = "^[0-9]{12,13}$", message = "Telefone deve ter 12 ou 13 dígitos com DDI.") String phone,
        @Email(message = "E-mail inválido.") String email,
        String cpf,
        boolean primary) {

        public ResidentInput toInput() {
            return new ResidentInput(id, name, phone, email, cpf, primary);
        }
    }

    public List<ResidentInput> toInputs() {
        return residents.stream().map(ResidentItem::toInput).toList();
    }
}
