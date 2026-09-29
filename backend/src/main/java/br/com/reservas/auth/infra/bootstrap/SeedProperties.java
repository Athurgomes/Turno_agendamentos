package br.com.reservas.auth.infra.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * D-42: dados do condominio/ADMIN iniciais, criados na subida se ainda nao
 * existirem. `adminEmail`/`adminPassword` vazios (`APP_SEED_ADMIN_PASSWORD`
 * ausente) fazem o bootstrap pular a criacao do ADMIN, sem falhar a subida.
 */
@ConfigurationProperties(prefix = "app.seed")
public record SeedProperties(String adminEmail, String adminPassword, String condominiumName,
    String defaultPaymentWhatsapp) {
}
