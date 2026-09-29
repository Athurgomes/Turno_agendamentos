package br.com.reservas.auth.infra.security;

import br.com.reservas.auth.infra.UserAccountRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

/**
 * JWT HS256 (D-42) com Spring Security OAuth2 Resource Server + Nimbus (sem
 * biblioteca extra de JWT). O {@link JwtDecoder} soma a validacao de
 * expiracao/emissao com {@link ActiveAccountTokenValidator} (RN-06). A
 * validacao de timestamp usa o mesmo {@link Clock} injetado na aplicacao
 * (RNF-10) que {@code JwtIssuer} usa para emitir o token — nunca
 * {@code Clock.systemUTC()} via {@code JwtValidators.createDefault()} — para
 * o relogio simulado do perfil {@code demo} (D-32, {@code APP_DEMO_NOW} no
 * passado ou no futuro) nao invalidar tokens emitidos sob o mesmo relogio.
 * A claim {@code role} (string unica, nao lista) vira uma unica
 * {@code ROLE_*} authority.
 */
@Configuration
public class JwtSecurityBeans {

    @Bean
    JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(properties.key()));
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties, UserAccountRepository accounts, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(properties.key())
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
            timestampValidator,
            new ActiveAccountTokenValidator(accounts));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(JwtSecurityBeans::authoritiesOf);
        return converter;
    }

    private static Collection<GrantedAuthority> authoritiesOf(Jwt jwt) {
        String role = jwt.getClaimAsString("role");
        return role == null ? List.of() : List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
