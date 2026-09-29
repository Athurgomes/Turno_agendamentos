package br.com.reservas.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** RF-AUT-02, RN-04 (politica de senha) e a flag `tempPassword` do contrato. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerPasswordTest extends AbstractIntegrationTest {

    private static final String CURRENT_PASSWORD = "SenhaAtual123";

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
    private String accessToken;
    private UUID accountId;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Senha");

        UserAccount account = accounts.save(new UserAccount(condominiumId, Role.ADMIN, null,
            "senha@exemplo.test", "Administracao", null, null, passwordEncoder.encode(CURRENT_PASSWORD)));
        accountId = account.getId();

        String response = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                // IP fixo dedicado a esta classe (ver AuthControllerRefreshTest.TEST_IP).
                .header("X-Forwarded-For", "198.51.100.20")
                .content(objectMapper.writeValueAsString(
                    Map.of("login", "senha@exemplo.test", "password", CURRENT_PASSWORD))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        accessToken = objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    @DisplayName("RF-AUT-02: senha atual incorreta devolve 422 INVALID_CURRENT_PASSWORD")
    void wrongCurrentPasswordIsRejected() throws Exception {
        changePassword("senha-errada", "NovaSenha123")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_CURRENT_PASSWORD"));
    }

    @Test
    @DisplayName("RN-04: nova senha curta demais devolve 422 WEAK_PASSWORD")
    void shortNewPasswordIsRejected() throws Exception {
        changePassword(CURRENT_PASSWORD, "abc123")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    @DisplayName("RN-04: nova senha igual a atual devolve 422 WEAK_PASSWORD")
    void newPasswordEqualToCurrentIsRejected() throws Exception {
        changePassword(CURRENT_PASSWORD, CURRENT_PASSWORD)
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("WEAK_PASSWORD"));
    }

    @Test
    @DisplayName("RN-04: sucesso zera tempPassword e revoga os refresh tokens ativos da conta")
    void successfulChangeClearsTempPasswordFlagAndRevokesRefreshTokens() throws Exception {
        changePassword(CURRENT_PASSWORD, "NovaSenhaForte123")
            .andExpect(status().isNoContent());

        String me = mockMvc.perform(get("/api/v1/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(me).get("tempPassword").asBoolean()).isFalse();

        assertThat(refreshTokens.findByUserIdAndRevokedAtIsNull(accountId)).isEmpty();
    }

    private org.springframework.test.web.servlet.ResultActions changePassword(String currentPassword,
        String newPassword) throws Exception {
        return mockMvc.perform(put("/api/v1/auth/password")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(
                Map.of("currentPassword", currentPassword, "newPassword", newPassword))));
    }
}
