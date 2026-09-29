package br.com.reservas.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.RefreshTokenRepository;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** D-42: refresh rotaciona o token; reuso de um token ja revogado revoga todas as sessoes da conta (roubo). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerRefreshTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SenhaForte123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID condominiumId;

    @BeforeEach
    void setUp() {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Refresh");
    }

    // IP fixo dedicado a esta classe: o LoginRateLimiter (singleton) e
    // compartilhado entre classes de teste com o mesmo contexto Spring; sem
    // isso, chamadas de login de outras classes no mesmo IP padrao (127.0.0.1)
    // poderiam estourar o limite de 10/min (RNF-02) e quebrar este teste.
    private static final String TEST_IP = "198.51.100.10";

    private String login(String email) throws Exception {
        accounts.save(new UserAccount(condominiumId, Role.ADMIN, null, email, "Administracao", null, null,
            passwordEncoder.encode(RAW_PASSWORD)));

        String setCookie = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", TEST_IP)
                .content(objectMapper.writeValueAsString(Map.of("login", email, "password", RAW_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        return extractCookieValue(setCookie);
    }

    @Test
    @DisplayName("D-42: /auth/refresh emite novo access token e ROTACIONA o cookie de refresh")
    void refreshRotatesToken() throws Exception {
        String firstRefreshToken = login("refresh1@exemplo.test");

        String secondSetCookie = mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", firstRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        String secondRefreshToken = extractCookieValue(secondSetCookie);
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // O primeiro token, ja revogado pela rotacao, nao pode ser usado de novo.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", firstRefreshToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("D-42: reuso de um refresh token ja revogado revoga TODAS as sessoes da conta (roubo)")
    void reusingRevokedTokenRevokesAllSessionsForAccount() throws Exception {
        String firstRefreshToken = login("roubo@exemplo.test");
        UUID accountId = accounts.findByLogin("roubo@exemplo.test").orElseThrow().getId();

        String secondSetCookie = mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", firstRefreshToken)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);
        String secondRefreshToken = extractCookieValue(secondSetCookie);

        // Reusar o primeiro token (ja revogado pela rotacao) e um sinal de roubo.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", firstRefreshToken)))
            .andExpect(status().isUnauthorized());

        // O segundo token (legitimo, emitido pela rotacao) tambem deve ter sido revogado.
        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", secondRefreshToken)))
            .andExpect(status().isUnauthorized());

        assertThat(refreshTokens.findByUserIdAndRevokedAtIsNull(accountId)).isEmpty();
    }

    @Test
    @DisplayName("D-42: /auth/logout revoga o refresh do cookie e apaga o cookie (rota publica)")
    void logoutRevokesRefreshTokenAndClearsCookie() throws Exception {
        String refreshToken = login("logout@exemplo.test");

        String setCookie = mockMvc.perform(post("/api/v1/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
            .andExpect(status().isNoContent())
            .andReturn().getResponse().getHeader(HttpHeaders.SET_COOKIE);

        assertThat(setCookie).contains("Max-Age=0");

        mockMvc.perform(post("/api/v1/auth/refresh")
                .cookie(new jakarta.servlet.http.Cookie("refresh_token", refreshToken)))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("D-42: /auth/logout sem cookie nao falha (idempotente, publico)")
    void logoutWithoutCookieIsNoop() throws Exception {
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isNoContent());
    }

    private static String extractCookieValue(String setCookieHeader) {
        String withoutName = setCookieHeader.substring("refresh_token=".length());
        return withoutName.substring(0, withoutName.indexOf(';'));
    }
}
