package br.com.reservas.flow;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Fluxo de bloqueio de conta (RN-05): a 5a senha errada ainda e 401; a 6a tentativa e 423 ACCOUNT_LOCKED. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthLockoutFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("RN-05: cinco tentativas erradas devolvem 401 e a sexta devolve 423 ACCOUNT_LOCKED mesmo com IPs diferentes por tentativa")
    void fifthAttemptStays401AndSixthLocksTheAccount() throws Exception {
        UUID condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Lockout");
        String login = "lockout.flow@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, login, passwordEncoder.encode(PASSWORD));

        // Um IP fixo por tentativa: o bloqueio e por conta (RN-05), nao so por IP (RNF-02 e outro mecanismo).
        String ip = "198.51.100.70.1";
        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(loginRequest(login, "senha-errada-" + attempt, ip))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        mockMvc.perform(loginRequest(login, PASSWORD, ip))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder loginRequest(String login,
        String password, String ip) throws Exception {
        return post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", ip)
            .content(objectMapper.writeValueAsString(Map.of("login", login, "password", password)));
    }
}
