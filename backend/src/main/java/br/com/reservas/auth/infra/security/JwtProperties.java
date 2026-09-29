package br.com.reservas.auth.infra.security;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code APP_JWT_SECRET} (RNF-02, D-42). O construtor compacto falha a subida
 * da aplicacao se o segredo tiver menos de 32 bytes, para nunca operar com uma
 * chave HS256 fraca.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret) {

    private static final int MIN_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                "APP_JWT_SECRET deve ter pelo menos 32 bytes; configure a variavel de ambiente (RNF-02).");
        }
    }

    SecretKey key() {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
