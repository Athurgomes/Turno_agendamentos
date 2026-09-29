package br.com.reservas.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * `APP_STORAGE_*` (D-27, D-41, D-46). Defaults sao os de desenvolvimento do
 * Versity S3 Gateway do `docker-compose.yml` local (`minioadmin`/`minioadmin`
 * sao credenciais convencionais de dev, nao um segredo real; em `demo`/`prod`
 * as variaveis de ambiente sobrescrevem todos os campos).
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String endpoint,
    String publicUrl,
    String bucket,
    String accessKey,
    String secretKey,
    String region,
    boolean createBucket) {
}
