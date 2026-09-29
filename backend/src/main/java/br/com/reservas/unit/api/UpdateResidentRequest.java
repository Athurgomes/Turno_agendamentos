package br.com.reservas.unit.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** `PUT /me/unit/residents/{id}` (RN-09): só nome/telefone/e-mail; CPF nunca é aceito aqui. */
public record UpdateResidentRequest(
    @NotBlank(message = "Nome é obrigatório.") String name,
    @NotBlank(message = "Telefone é obrigatório.")
    @Pattern(regexp = "^[0-9]{12,13}$", message = "Telefone deve ter 12 ou 13 dígitos com DDI.") String phone,
    @Email(message = "E-mail inválido.") String email) {
}
