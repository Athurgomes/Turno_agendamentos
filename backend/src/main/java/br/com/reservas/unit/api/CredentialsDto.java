package br.com.reservas.unit.api;

import br.com.reservas.unit.application.Credentials;

/** Senha exibida uma unica vez (RF-UNI-02/03/04). */
public record CredentialsDto(String username, String tempPassword) {

    public static CredentialsDto from(Credentials credentials) {
        return new CredentialsDto(credentials.username(), credentials.tempPassword());
    }
}
