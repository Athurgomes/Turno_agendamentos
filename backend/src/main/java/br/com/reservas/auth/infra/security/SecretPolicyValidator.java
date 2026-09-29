package br.com.reservas.auth.infra.security;

import br.com.reservas.auth.infra.bootstrap.SeedProperties;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * D-48: fora do perfil {@code local}, a aplicação recusa subir com o segredo
 * JWT de exemplo do repositório ({@link ExampleSecrets#JWT_SECRET}); no
 * perfil {@code demo}, o mesmo vale para a senha ADMIN de exemplo
 * ({@link ExampleSecrets#ADMIN_PASSWORD}). A checagem roda no construtor, na
 * subida do contexto Spring, para falhar cedo (RNF-02).
 */
@Component
public class SecretPolicyValidator {

    public SecretPolicyValidator(Environment environment, JwtProperties jwtProperties, SeedProperties seed) {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        if (!local && ExampleSecrets.JWT_SECRET.equals(jwtProperties.secret())) {
            throw new IllegalStateException(
                "Defina APP_JWT_SECRET no .env com um segredo próprio (>= 32 bytes); o valor de "
                    + "exemplo do repositório não pode ser usado fora do perfil local (D-48).");
        }

        boolean demo = environment.acceptsProfiles(Profiles.of("demo"));
        if (demo && ExampleSecrets.ADMIN_PASSWORD.equals(seed.adminPassword())) {
            throw new IllegalStateException(
                "Defina APP_SEED_ADMIN_PASSWORD no .env com uma senha própria; o valor de exemplo "
                    + "do repositório não pode ser usado no perfil demo (D-48).");
        }
    }
}
