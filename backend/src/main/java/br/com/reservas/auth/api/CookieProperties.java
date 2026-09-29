package br.com.reservas.auth.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** `APP_COOKIE_SECURE` (RNF-02): `false` em dev local sem HTTPS, `true` em demo/prod. */
@ConfigurationProperties(prefix = "app.cookie")
public record CookieProperties(boolean secure) {
}
