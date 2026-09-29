package br.com.reservas.auth.domain;

import br.com.reservas.shared.error.BusinessException;
import org.springframework.http.HttpStatus;

/**
 * RN-04: política de senha nova. Mínimo 8 caracteres, letras e números,
 * diferente da senha atual (temporária ou não) e do identificador da conta
 * (usuário da unidade ou e-mail de ADMIN/SÍNDICO). Violação vira
 * {@code 422 WEAK_PASSWORD} com o detalhe da regra descumprida.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;

    private PasswordPolicy() {
    }

    public static void validate(String newPassword, String currentPassword, String username, String email) {
        if (newPassword == null || newPassword.length() < MIN_LENGTH) {
            throw weak("A nova senha deve ter pelo menos 8 caracteres.");
        }
        if (!newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
            throw weak("A nova senha deve conter letras e números.");
        }
        if (newPassword.equals(currentPassword)) {
            throw weak("A nova senha deve ser diferente da senha atual.");
        }
        if (username != null && newPassword.equalsIgnoreCase(username)) {
            throw weak("A nova senha não pode ser igual ao usuário.");
        }
        if (email != null && newPassword.equalsIgnoreCase(email)) {
            throw weak("A nova senha não pode ser igual ao e-mail.");
        }
    }

    private static BusinessException weak(String detail) {
        return new BusinessException("WEAK_PASSWORD", HttpStatus.UNPROCESSABLE_ENTITY, detail);
    }
}
