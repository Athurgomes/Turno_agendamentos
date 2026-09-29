package br.com.reservas.settings.api;

public record CreateSyndicResponse(SyndicDto syndic, Credentials credentials) {

    public record Credentials(String username, String tempPassword) {
    }
}
