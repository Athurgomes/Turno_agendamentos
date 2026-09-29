package br.com.reservas.auth.application;

import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.UserAccountRepository;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

/**
 * Único ponto que lê a conta autenticada do contexto de segurança (RN-01).
 * Os claims do JWT trazem só {@code sub}/{@code role} (D-42); {@code unitId}
 * e {@code condominiumId} vêm de uma leitura da conta, pois não valeria a
 * pena inchar o token só por isso.
 */
@Service
public class CurrentUserProvider {

    private final UserAccountRepository accounts;

    public CurrentUserProvider(UserAccountRepository accounts) {
        this.accounts = accounts;
    }

    public CurrentUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new IllegalStateException("Nenhuma conta autenticada no contexto de segurança.");
        }
        UUID accountId = UUID.fromString(jwtAuthentication.getToken().getSubject());
        UserAccount account = accounts.findById(accountId)
            .orElseThrow(() -> new IllegalStateException("Conta do token não encontrada."));
        return new CurrentUser(account.getId(), account.getRole(), account.getUnitId(), account.getCondominiumId());
    }
}
