package br.com.reservas.auth.api;

import br.com.reservas.auth.application.AuthResult;

/** Resposta de `/auth/login` e `/auth/refresh` (docs/03-api.md, D-42). */
public record LoginResponse(String accessToken, long expiresIn, UserDto user) {

    public static LoginResponse from(AuthResult result) {
        return new LoginResponse(result.accessToken(), result.expiresInSeconds(), UserDto.from(result.user()));
    }
}
