package br.com.reservas.auth.application;

/** Resultado de login/refresh: access token pronto (JWT) + refresh token bruto (para o cookie) + sessao. */
public record AuthResult(String accessToken, long expiresInSeconds, String refreshToken, SessionUser user) {
}
