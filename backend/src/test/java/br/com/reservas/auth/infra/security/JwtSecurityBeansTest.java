package br.com.reservas.auth.infra.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.auth.application.JwtIssuer;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.support.AbstractIntegrationTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * RNF-10/D-32: o {@code JwtDecoder} precisa validar {@code exp}/{@code iat}
 * com o mesmo {@link Clock} que o {@code JwtIssuer} usa para emitir — nunca
 * com o relógio do sistema — senão um token emitido sob o relógio simulado do
 * perfil {@code demo} ({@code APP_DEMO_NOW} no passado) seria sempre rejeitado
 * como expirado assim que o Clock da aplicação ficasse defasado em relação ao
 * relógio real da máquina.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(JwtSecurityBeansTest.FixedPastClockConfig.class)
@Transactional
class JwtSecurityBeansTest extends AbstractIntegrationTest {

    private static final Instant PAST_NOW = Instant.parse("2020-01-01T00:00:00Z");

    @TestConfiguration
    static class FixedPastClockConfig {

        @Bean
        @Primary
        Clock fixedPastClock() {
            return Clock.fixed(PAST_NOW, ZoneOffset.UTC);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtIssuer jwtIssuer;

    @Test
    @DisplayName("D-32/RNF-10: token emitido sob um Clock fixo no passado (2020) é aceito por request no mesmo Clock")
    void acceptsTokenIssuedUnderPastSimulatedClock() throws Exception {
        UUID condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Demo");
        UserAccount account = accounts.save(new UserAccount(condominiumId, Role.ADMIN, null,
            "admin.demo.passado@exemplo.test", "Admin Demo", null, null, passwordEncoder.encode("SenhaForte123")));

        String token = jwtIssuer.issue(account);

        mockMvc.perform(get("/api/v1/test-roles/any-authenticated")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }
}
