package br.com.reservas.auth.infra.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RNF-02: `APP_JWT_SECRET` precisa ter pelo menos 32 bytes, senao a subida falha. */
class JwtPropertiesTest {

    @Test
    @DisplayName("RNF-02: segredo com menos de 32 bytes falha na construcao (subida da aplicacao)")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new JwtProperties("muito-curto"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("RNF-02: segredo ausente falha na construcao")
    void rejectsMissingSecret() {
        assertThatThrownBy(() -> new JwtProperties(null))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("RNF-02: segredo com 32 bytes ou mais e aceito")
    void acceptsSecretWithAtLeast32Bytes() {
        assertThatCode(() -> new JwtProperties("01234567890123456789012345678901"))
            .doesNotThrowAnyException();
    }
}
