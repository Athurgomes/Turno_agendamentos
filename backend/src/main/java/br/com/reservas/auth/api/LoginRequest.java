package br.com.reservas.auth.api;

import jakarta.validation.constraints.NotBlank;

/** `POST /auth/login` (docs/03-api.md). */
public record LoginRequest(@NotBlank(message = "Login é obrigatório.") String login,
    @NotBlank(message = "Senha é obrigatória.") String password) {
}
