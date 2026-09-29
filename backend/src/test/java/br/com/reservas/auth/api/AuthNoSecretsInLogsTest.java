package br.com.reservas.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.support.AbstractIntegrationTest;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** CLAUDE.md secao 7: nunca logar senha, token, hash ou CPF — nem numa tentativa de login invalida. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthNoSecretsInLogsTest extends AbstractIntegrationTest {

    private static final String REAL_PASSWORD = "SenhaForte123";
    private static final String ATTEMPTED_PASSWORD = "TentativaSecreta987";

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

    private ListAppender<ILoggingEvent> appender;
    private Logger rootLogger;

    @BeforeEach
    void attachAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        rootLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("Nunca loga a senha em texto plano nem o hash BCrypt, nem em uma tentativa de login invalida")
    void loginAttemptNeverLogsPasswordOrHash() throws Exception {
        UUID condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Log");
        UserAccount account = accounts.save(new UserAccount(condominiumId, Role.ADMIN, null, "log@exemplo.test",
            "Administracao", null, null, passwordEncoder.encode(REAL_PASSWORD)));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                // IP fixo dedicado a esta classe (ver AuthControllerRefreshTest.TEST_IP).
                .header("X-Forwarded-For", "198.51.100.40")
                .content(objectMapper.writeValueAsString(
                    Map.of("login", "log@exemplo.test", "password", ATTEMPTED_PASSWORD))))
            .andExpect(status().isUnauthorized());

        String allLogs = appender.list.stream()
            .map(ILoggingEvent::getFormattedMessage)
            .reduce("", (a, b) -> a + "\n" + b);

        assertThat(allLogs).doesNotContain(ATTEMPTED_PASSWORD);
        assertThat(allLogs).doesNotContain(REAL_PASSWORD);
        assertThat(allLogs).doesNotContain(account.getPasswordHash());
    }
}
