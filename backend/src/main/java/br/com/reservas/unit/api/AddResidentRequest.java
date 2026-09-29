package br.com.reservas.unit.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** `POST /me/unit/residents` (RN-09): adiciona morador adicional. */
public record AddResidentRequest(
    @NotBlank(message = "Nome é obrigatório.") String name,
    @NotBlank(message = "Telefone é obrigatório.")
    @Pattern(regexp = "^[0-9]{12,13}$", message = "Telefone deve ter 12 ou 13 dígitos com DDI.") String phone,
    @Email(message = "E-mail inválido.") String email,
    String cpf) {
}
