package br.com.reservas.shared.security;

import br.com.reservas.shared.web.CorrelationIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Autenticacao stateless via JWT (F1, D-42): access token Bearer (Spring
 * Security OAuth2 Resource Server), sem sessao de servidor, sem login por
 * formulario/basic. CSRF fica desligado: nao ha cookie de sessao classico
 * enviado automaticamente pelo navegador em toda origem — o unico cookie e o
 * `refresh_token`, com `SameSite=Strict` e `Path=/api/v1/auth` (o navegador so
 * o envia em navegacao de mesmo site e so para essas rotas); as rotas de
 * negocio exigem o header `Authorization: Bearer`, que o navegador nunca
 * anexa sozinho a um pedido cross-site, o que ja neutraliza CSRF nelas.
 * Caminhos publicos documentados em D-40/D-42, autorizacao por perfil via
 * {@code @PreAuthorize} nos controllers (metodo habilitado abaixo), e
 * respostas 401/403 no formato ProblemDetail (docs/03).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final String[] PUBLIC_PATHS = {
        "/actuator/health",
        "/api/v3/api-docs/**",
        "/api/swagger-ui/**",
        "/api/swagger-ui.html",
        "/api/v1/system/**",
        "/api/v1/auth/login",
        "/api/v1/auth/refresh",
        "/api/v1/auth/logout"
    };

    private final String publicUrl;
    private final ObjectMapper objectMapper;
    private final JwtDecoder jwtDecoder;
    private final JwtAuthenticationConverter jwtAuthenticationConverter;

    public SecurityConfig(@Value("${app.public-url}") String publicUrl, ObjectMapper objectMapper,
        JwtDecoder jwtDecoder, JwtAuthenticationConverter jwtAuthenticationConverter) {
        this.publicUrl = publicUrl;
        this.objectMapper = objectMapper;
        this.jwtDecoder = jwtDecoder;
        this.jwtAuthenticationConverter = jwtAuthenticationConverter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(basic -> basic.disable())
            .formLogin(form -> form.disable())
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper))
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(jwtAuthenticationConverter))
                .authenticationEntryPoint(entryPoint)
            )
            .addFilterBefore(new CorrelationIdFilter(), UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(PUBLIC_PATHS).permitAll()
                .anyRequest().authenticated()
            );
        return http.build();
    }

    /**
     * RNF-02/D-26: o front chama sempre a mesma origem (proxy do nginx), entao
     * esta config e defensiva, nao o caminho normal de uso: so libera
     * {@code app.public-url} (default {@code http://localhost}), com credenciais.
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(publicUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER));
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
