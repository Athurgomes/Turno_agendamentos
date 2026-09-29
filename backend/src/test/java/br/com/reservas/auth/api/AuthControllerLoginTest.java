package br.com.reservas.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.auth.support.MutableClock;
import br.com.reservas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF-AUT-01 (login por usuario ou e-mail), RN-05 (bloqueio) e RNF-02 (rate
 * limit) de ponta a ponta via MockMvc + Postgres real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerLoginTest extends AbstractIntegrationTest {

    private static final Instant START = Instant.parse("2026-01-01T10:00:00Z");
    private static final String RAW_PASSWORD = "SenhaForte123";

    @TestConfiguration
    static class ClockConfig {
        @Bean
        @Primary
        Clock mutableClock() {
            return new MutableClock(START, ZoneOffset.UTC);
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
    private ObjectMapper objectMapper;

    @Autowired
    private Clock clock;

    private static final AtomicInteger IP_SEQUENCE = new AtomicInteger();

    private UUID condominiumId;

    @BeforeEach
    void setUp() {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Teste");
    }

    @Test
    @DisplayName("RF-AUT-01: ADMIN/SYNDIC fazem login por e-mail (case-insensitive)")
    void loginByEmail() throws Exception {
        accounts.save(new UserAccount(condominiumId, Role.ADMIN, null, "admin.login@exemplo.test", "Administracao",
            null, null, passwordEncoder.encode(RAW_PASSWORD)));

        mockMvc.perform(loginRequest("ADMIN.LOGIN@exemplo.test", RAW_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.expiresIn").value(900))
            .andExpect(jsonPath("$.user.role").value("ADMIN"))
            .andExpect(jsonPath("$.user.name").value("Administracao"))
            .andExpect(jsonPath("$.user.unitId").value(nullValue()))
            .andExpect(jsonPath("$.user.tempPassword").value(true));
    }

    @Test
    @DisplayName("RF-AUT-01: conta UNIT faz login por usuario (case-insensitive)")
    void loginByUsername() throws Exception {
        UUID unitId = UUID.randomUUID();
        jdbcTemplate.update("insert into unit (id, condominium_id, number, identifier) values (?, ?, ?, ?)",
            unitId, condominiumId, "10a", "10a");
        accounts.save(new UserAccount(condominiumId, Role.UNIT, "unidade10a", null, null, null, unitId,
            passwordEncoder.encode(RAW_PASSWORD)));

        mockMvc.perform(loginRequest("UNIDADE10A", RAW_PASSWORD))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.user.role").value("UNIT"))
            .andExpect(jsonPath("$.user.name").value("Unidade unidade10a"))
            .andExpect(jsonPath("$.user.unitIdentifier").value("unidade10a"));
    }

    @Test
    @DisplayName("RN-05: login com conta inexistente devolve 401 INVALID_CREDENTIALS (sem revelar que nao existe)")
    void nonExistentAccountReturnsInvalidCredentials() throws Exception {
        mockMvc.perform(loginRequest("nao.existe@exemplo.test", "qualquer123"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("RN-05: a 5a tentativa errada ainda devolve 401; a 6a devolve 423 ACCOUNT_LOCKED mesmo com senha certa")
    void sixthAttemptIsLockedEvenWithCorrectPassword() throws Exception {
        accounts.save(new UserAccount(condominiumId, Role.ADMIN, null, "bloqueio@exemplo.test", "Administracao",
            null, null, passwordEncoder.encode(RAW_PASSWORD)));
        String ip = nextIp();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(loginRequest("bloqueio@exemplo.test", "senha-errada", ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(loginRequest("bloqueio@exemplo.test", RAW_PASSWORD, ip))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("RN-05: apos 15 minutos de bloqueio, a conta volta a logar normalmente")
    void accountUnlocksAfterFifteenMinutes() throws Exception {
        accounts.save(new UserAccount(condominiumId, Role.ADMIN, null, "desbloqueio@exemplo.test", "Administracao",
            null, null, passwordEncoder.encode(RAW_PASSWORD)));
        String ip = nextIp();

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(loginRequest("desbloqueio@exemplo.test", "senha-errada", ip))
                .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(loginRequest("desbloqueio@exemplo.test", RAW_PASSWORD, ip))
            .andExpect(status().isLocked());

        ((MutableClock) clock).advance(Duration.ofMinutes(15).plusSeconds(1));

        mockMvc.perform(loginRequest("desbloqueio@exemplo.test", RAW_PASSWORD, ip))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RNF-02: 11a requisicao de login no mesmo minuto e pelo mesmo IP devolve 429 TOO_MANY_REQUESTS")
    void rateLimitBlocksEleventhRequestPerMinute() throws Exception {
        String ip = nextIp();
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(loginRequest("qualquer@exemplo.test", "senha-errada", ip))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(loginRequest("qualquer@exemplo.test", "senha-errada", ip))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("D-42: cookie de refresh sai com HttpOnly, SameSite=Strict, Path=/api/v1/auth e Max-Age de 7 dias")
    void loginSetsRefreshCookieWithExpectedAttributes() throws Exception {
        accounts.save(new UserAccount(condominiumId, Role.ADMIN, null, "cookie@exemplo.test", "Administracao",
            null, null, passwordEncoder.encode(RAW_PASSWORD)));

        String setCookie = mockMvc.perform(loginRequest("cookie@exemplo.test", RAW_PASSWORD))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("refresh_token=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
        assertThat(setCookie).contains("Path=/api/v1/auth");
        assertThat(setCookie).contains("Max-Age=604800");
        assertThat(setCookie).doesNotContain("Secure"); // APP_COOKIE_SECURE=false no perfil de teste.
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String login,
        String password) throws Exception {
        return loginRequest(login, password, nextIp());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String login,
        String password, String ip) throws Exception {
        return post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", ip)
            .content(objectMapper.writeValueAsString(Map.of("login", login, "password", password)));
    }

    private static String nextIp() {
        return "203.0.113." + IP_SEQUENCE.incrementAndGet();
    }
}
