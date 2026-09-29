package br.com.reservas.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.shared.error.BusinessException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** RN-04: nova senha com >= 8 caracteres, letras e numeros, diferente da atual, do usuario e do e-mail. */
class PasswordPolicyTest {

    @Test
    @DisplayName("RN-04: senha com menos de 8 caracteres e recusada (WEAK_PASSWORD)")
    void rejectsShortPassword() {
        assertViolation(() -> PasswordPolicy.validate("ab12345", "K7M4XP", "unidade1a", null));
    }

    @Test
    @DisplayName("RN-04: senha sem numero e recusada (WEAK_PASSWORD)")
    void rejectsPasswordWithoutDigit() {
        assertViolation(() -> PasswordPolicy.validate("somenteletras", "K7M4XP", "unidade1a", null));
    }

    @Test
    @DisplayName("RN-04: senha sem letra e recusada (WEAK_PASSWORD)")
    void rejectsPasswordWithoutLetter() {
        assertViolation(() -> PasswordPolicy.validate("12345678", "K7M4XP", "unidade1a", null));
    }

    @Test
    @DisplayName("RN-04: nova senha igual a senha atual e recusada (WEAK_PASSWORD)")
    void rejectsPasswordEqualToCurrent() {
        assertViolation(() -> PasswordPolicy.validate("Senha123", "Senha123", "unidade1a", null));
    }

    @Test
    @DisplayName("RN-04: nova senha igual ao usuario e recusada (WEAK_PASSWORD)")
    void rejectsPasswordEqualToUsername() {
        assertViolation(() -> PasswordPolicy.validate("Unidade1a", "K7M4XP", "unidade1a", null));
    }

    @Test
    @DisplayName("RN-04: nova senha igual ao e-mail e recusada (WEAK_PASSWORD)")
    void rejectsPasswordEqualToEmail() {
        assertViolation(() -> PasswordPolicy.validate("Admin@Exemplo.Test", "K7M4XP", null, "admin@exemplo.test"));
    }

    @Test
    @DisplayName("RN-04: senha que cumpre todas as regras e aceita")
    void acceptsCompliantPassword() {
        assertThatCode(() -> PasswordPolicy.validate("NovaSenha123", "K7M4XP", "unidade1a", null))
            .doesNotThrowAnyException();
    }

    private void assertViolation(ThrowingCallable callable) {
        assertThatThrownBy(callable)
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> {
                BusinessException business = (BusinessException) ex;
                assertThat(business.code()).isEqualTo("WEAK_PASSWORD");
                assertThat(business.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
            });
    }
}
