package br.com.reservas.auth.infra.security;

import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.UserAccountRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * RN-06: a cada requisição autenticada confere que a conta segue ativa e que
 * a versão do token (claim {@code ver}) bate com {@code token_version}
 * atual; senão o access token, mesmo dentro da validade, é tratado como
 * inválido (a conta foi desativada ou a senha/sessão foi revogada).
 */
public class ActiveAccountTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_ACCOUNT =
        new OAuth2Error("invalid_token", "Conta inativa ou sessão revogada.", null);

    private final UserAccountRepository accounts;

    public ActiveAccountTokenValidator(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        UUID accountId = parseSubject(token.getSubject());
        // Nimbus desserializa números JSON como Long, mesmo quando codificados
        // a partir de um int (JwtIssuer grava `ver` como int); por isso lê como
        // Number, nunca faz cast direto para Integer.
        Number tokenVersion = token.getClaim("ver");
        if (accountId == null || tokenVersion == null) {
            return OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
        }

        Optional<UserAccount> account = accounts.findById(accountId);
        boolean valid = account.isPresent()
            && account.get().isActive()
            && account.get().getTokenVersion() == tokenVersion.intValue();

        return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_ACCOUNT);
    }

    private static UUID parseSubject(String subject) {
        try {
            return subject == null ? null : UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
