package br.com.reservas.auth.infra.security;

/**
 * D-48: valores de exemplo usados nos defaults de dev do perfil {@code local}
 * ({@code application-local.yml}), do {@code docker-compose.yml} e do
 * {@code .env.example}. Centralizados aqui para que {@link SecretPolicyValidator}
 * recuse subir fora do perfil {@code local} (ou, para a senha, fora do
 * {@code demo}) com um desses valores — nunca altere um sem atualizar os
 * outros dois arquivos.
 */
public final class ExampleSecrets {

    public static final String JWT_SECRET = "dev-only-secret-nao-usar-em-producao-0123456789";
    public static final String ADMIN_PASSWORD = "troque-esta-senha";

    private ExampleSecrets() {
    }
}
