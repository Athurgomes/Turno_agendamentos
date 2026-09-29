package br.com.reservas.auth.api;

import jakarta.validation.constraints.NotBlank;

/** `PUT /auth/password` (RN-04). */
public record ChangePasswordRequest(@NotBlank(message = "Senha atual é obrigatória.") String currentPassword,
    @NotBlank(message = "Nova senha é obrigatória.") String newPassword) {
}
