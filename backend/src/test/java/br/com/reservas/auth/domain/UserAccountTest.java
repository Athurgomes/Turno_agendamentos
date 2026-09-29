package br.com.reservas.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-05 (bloqueio por tentativas) e RN-06 (invalidacao de sessao) vivem na entidade. */
class UserAccountTest {

    private UserAccount newAccount() {
        return new UserAccount(UUID.randomUUID(), Role.UNIT, "unidade1a", null, null, null, UUID.randomUUID(),
            "hash");
    }

    @Test
    @DisplayName("RN-05: a 5a tentativa invalida seguida bloqueia a conta por 15 minutos")
    void locksAccountOnFifthFailedAttempt() {
        UserAccount account = newAccount();
        Instant now = Instant.parse("2026-01-01T10:00:00Z");

        for (int i = 0; i < 4; i++) {
            account.registerFailedAttempt(now);
        }
        assertThat(account.isLocked(now)).isFalse();

        account.registerFailedAttempt(now);

        assertThat(account.isLocked(now)).isTrue();
        assertThat(account.isLocked(now.plusSeconds(15 * 60 - 1))).isTrue();
        assertThat(account.isLocked(now.plusSeconds(15 * 60 + 1))).isFalse();
    }

    @Test
    @DisplayName("RN-05: login correto zera o contador de tentativas e o bloqueio")
    void successfulLoginResetsFailedAttempts() {
        UserAccount account = newAccount();
        Instant now = Instant.parse("2026-01-01T10:00:00Z");
        for (int i = 0; i < 5; i++) {
            account.registerFailedAttempt(now);
        }
        assertThat(account.isLocked(now)).isTrue();

        account.registerSuccessfulLogin();

        assertThat(account.isLocked(now)).isFalse();
        assertThat(account.getFailedAttempts()).isZero();
    }

    @Test
    @DisplayName("RN-06: desativar a conta incrementa token_version para invalidar sessoes ativas")
    void deactivateIncrementsTokenVersion() {
        UserAccount account = newAccount();
        int before = account.getTokenVersion();

        account.deactivate();

        assertThat(account.isActive()).isFalse();
        assertThat(account.getTokenVersion()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("RN-03/RN-06: reset administrativo gera senha temporaria e tambem invalida sessoes ativas")
    void resetToTemporaryPasswordInvalidatesSessions() {
        UserAccount account = newAccount();
        int before = account.getTokenVersion();

        account.resetToTemporaryPassword("novo-hash");

        assertThat(account.isTempPassword()).isTrue();
        assertThat(account.getPasswordHash()).isEqualTo("novo-hash");
        assertThat(account.getTokenVersion()).isEqualTo(before + 1);
    }
}
