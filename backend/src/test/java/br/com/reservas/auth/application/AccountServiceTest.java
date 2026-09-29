package br.com.reservas.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.RefreshTokenRepository;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.support.AbstractIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** Interface publica do modulo `auth` para os demais modulos criarem/gerenciarem contas (F2+). */
@SpringBootTest
class AccountServiceTest extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private UserAccountRepository accounts;

    @Autowired
    private RefreshTokenRepository refreshTokens;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID condominiumId;

    @BeforeEach
    void setUp() {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Account");
    }

    // user_account.unit_id ganhou FK para unit na V3.
    private UUID insertUnit(String number) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into unit (id, condominium_id, number, identifier) values (?, ?, ?, ?)",
            id, condominiumId, number, number);
        return id;
    }

    @Test
    @Transactional
    @DisplayName("RN-02/RN-03: createUnitAccount cria a conta UNIT com senha temporaria valida (BCrypt)")
    void createUnitAccountGeneratesUsableTemporaryPassword() {
        UUID unitId = insertUnit("30a");
        String tempPassword = accountService.createUnitAccount(condominiumId, unitId, "unidade30a");

        UserAccount account = accounts.findByLogin("unidade30a").orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.UNIT);
        assertThat(account.getUnitId()).isEqualTo(unitId);
        assertThat(account.isTempPassword()).isTrue();
        assertThat(passwordEncoder.matches(tempPassword, account.getPasswordHash())).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("RF-UNI-07: createSyndic cria a conta SYNDIC com login por e-mail")
    void createSyndicCreatesAccountWithEmailLogin() {
        String tempPassword = accountService.createSyndic(condominiumId, "Sindico Teste", "sindico@exemplo.test",
            "5562999998888");

        UserAccount account = accounts.findByLogin("sindico@exemplo.test").orElseThrow();
        assertThat(account.getRole()).isEqualTo(Role.SYNDIC);
        assertThat(passwordEncoder.matches(tempPassword, account.getPasswordHash())).isTrue();
    }

    @Test
    @Transactional
    @DisplayName("RN-03/RN-06: resetPassword troca para uma nova senha temporaria e revoga as sessoes ativas")
    void resetPasswordIssuesNewTemporaryPasswordAndRevokesSessions() {
        UUID unitId = insertUnit("31a");
        accountService.createUnitAccount(condominiumId, unitId, "unidade31a");
        UserAccount account = accounts.findByLogin("unidade31a").orElseThrow();
        refreshTokens.save(new br.com.reservas.auth.domain.RefreshToken(account.getId(), "hash-ativo",
            Instant.now().plusSeconds(3600)));

        String newTempPassword = accountService.resetPassword(account.getId());

        UserAccount reloaded = accounts.findById(account.getId()).orElseThrow();
        assertThat(reloaded.isTempPassword()).isTrue();
        assertThat(passwordEncoder.matches(newTempPassword, reloaded.getPasswordHash())).isTrue();
        assertThat(refreshTokens.findByUserIdAndRevokedAtIsNull(account.getId())).isEmpty();
    }

    @Test
    @Transactional
    @DisplayName("RN-06: deactivate desativa a conta, incrementa token_version e revoga as sessoes ativas")
    void deactivateDisablesAccountAndRevokesSessions() {
        String tempPassword = accountService.createSyndic(condominiumId, "Sindico Desativar",
            "desativar.sindico@exemplo.test", "5562999998888");
        UserAccount account = accounts.findByLogin("desativar.sindico@exemplo.test").orElseThrow();
        int versionBefore = account.getTokenVersion();
        refreshTokens.save(new br.com.reservas.auth.domain.RefreshToken(account.getId(), "hash-ativo-2",
            Instant.now().plusSeconds(3600)));

        accountService.deactivate(account.getId());

        UserAccount reloaded = accounts.findById(account.getId()).orElseThrow();
        assertThat(reloaded.isActive()).isFalse();
        assertThat(reloaded.getTokenVersion()).isEqualTo(versionBefore + 1);
        assertThat(refreshTokens.findByUserIdAndRevokedAtIsNull(account.getId())).isEmpty();
        assertThat(tempPassword).isNotBlank();
    }
}
