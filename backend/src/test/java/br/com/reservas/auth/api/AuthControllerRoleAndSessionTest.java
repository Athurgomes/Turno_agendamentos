package br.com.reservas.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.auth.application.AccountService;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
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

/**
 * RN-01 (autorizacao por perfil, {@code @PreAuthorize}) e RN-06 (revogar a
 * sessao ao desativar a conta invalida o access token mesmo antes de expirar).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerRoleAndSessionTest extends AbstractIntegrationTest {

    private static final String RAW_PASSWORD = "SenhaForte123";

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
    private AccountService accountService;

    private UUID condominiumId;

    @BeforeEach
    void setUp() {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Role");
    }

    // IPs fixos e distintos por chamada, dedicados a esta classe (ver
    // AuthControllerRefreshTest.TEST_IP sobre o LoginRateLimiter compartilhado).
    private static final java.util.concurrent.atomic.AtomicInteger IP_SEQUENCE =
        new java.util.concurrent.atomic.AtomicInteger();

    private String login(Role role, String login, String rawPassword) throws Exception {
        UUID unitId = null;
        if (role == Role.UNIT) {
            unitId = UUID.randomUUID();
            jdbcTemplate.update("insert into unit (id, condominium_id, number, identifier) values (?, ?, ?, ?)",
                unitId, condominiumId, login, login);
        }
        accounts.save(new UserAccount(condominiumId, role,
            role == Role.UNIT ? login : null,
            role == Role.UNIT ? null : login,
            "Conta " + role, null, unitId, passwordEncoder.encode(rawPassword)));

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", "198.51.100.30." + IP_SEQUENCE.incrementAndGet())
                .content(objectMapper.writeValueAsString(Map.of("login", login, "password", rawPassword))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    @DisplayName("RN-01: sem token, rota autenticada devolve 401 UNAUTHENTICATED")
    void noTokenIsUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/test-roles/any-authenticated"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RN-01: token de conta UNIT nao acessa rota so-ADMIN (403 FORBIDDEN)")
    void unitTokenCannotAccessAdminOnlyRoute() throws Exception {
        String token = login(Role.UNIT, "unidade20a", RAW_PASSWORD);

        mockMvc.perform(get("/api/v1/test-roles/admin-only")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("RN-01: token de conta ADMIN acessa rota so-ADMIN")
    void adminTokenCanAccessAdminOnlyRoute() throws Exception {
        String token = login(Role.ADMIN, "admin.role@exemplo.test", RAW_PASSWORD);

        mockMvc.perform(get("/api/v1/test-roles/admin-only")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("RN-06: desativar a conta invalida o access token ja emitido (token_version muda)")
    void deactivatingAccountInvalidatesExistingAccessToken() throws Exception {
        String login = "desativar@exemplo.test";
        String token = login(Role.ADMIN, login, RAW_PASSWORD);
        UUID accountId = accounts.findByLogin(login).orElseThrow().getId();

        mockMvc.perform(get("/api/v1/test-roles/any-authenticated")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());

        accountService.deactivate(accountId);

        mockMvc.perform(get("/api/v1/test-roles/any-authenticated")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }
}
