package br.com.reservas.settings.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateSyndicRequest(
    @NotBlank(message = "Nome é obrigatório.") String name,
    @NotBlank(message = "E-mail é obrigatório.") @Email(message = "E-mail inválido.") String email,
    @NotBlank(message = "Telefone é obrigatório.")
    @Pattern(regexp = "^[0-9]{12,13}$", message = "Telefone deve ter 12 ou 13 dígitos com DDI.") String phone) {
}
