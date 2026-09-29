package br.com.reservas.auth.infra.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.auth.infra.bootstrap.SeedProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/** D-48/RNF-10: recusa subir fora do perfil local com segredos de exemplo. */
class SecretPolicyValidatorTest {

    private static final String OWN_SECRET = "segredo-proprio-com-mais-de-32-bytes-abc";

    @Test
    @DisplayName("D-48: perfil demo com APP_JWT_SECRET igual ao valor de exemplo nao sobe")
    void rejectsExampleJwtSecretOutsideLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");
        JwtProperties jwt = new JwtProperties(ExampleSecrets.JWT_SECRET);
        SeedProperties seed = new SeedProperties("admin@exemplo.test", "senha-forte-unica", "Cond", null);

        assertThatThrownBy(() -> new SecretPolicyValidator(env, jwt, seed))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_JWT_SECRET");
    }

    @Test
    @DisplayName("D-48: perfil local aceita o segredo JWT de exemplo do repositorio")
    void acceptsExampleJwtSecretInLocal() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("local");
        JwtProperties jwt = new JwtProperties(ExampleSecrets.JWT_SECRET);
        SeedProperties seed = new SeedProperties(null, null, null, null);

        assertThatCode(() -> new SecretPolicyValidator(env, jwt, seed)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("D-48: perfil demo com APP_SEED_ADMIN_PASSWORD igual ao valor de exemplo nao sobe")
    void rejectsExampleAdminPasswordInDemo() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");
        JwtProperties jwt = new JwtProperties(OWN_SECRET);
        SeedProperties seed = new SeedProperties("admin@exemplo.test", ExampleSecrets.ADMIN_PASSWORD, "Cond", null);

        assertThatThrownBy(() -> new SecretPolicyValidator(env, jwt, seed))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APP_SEED_ADMIN_PASSWORD");
    }

    @Test
    @DisplayName("D-48: perfil demo com segredo JWT e senha ADMIN proprios sobe normalmente")
    void acceptsOwnSecretsInDemo() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("demo");
        JwtProperties jwt = new JwtProperties(OWN_SECRET);
        SeedProperties seed = new SeedProperties("admin@exemplo.test", "senha-forte-unica", "Cond", null);

        assertThatCode(() -> new SecretPolicyValidator(env, jwt, seed)).doesNotThrowAnyException();
    }
}
