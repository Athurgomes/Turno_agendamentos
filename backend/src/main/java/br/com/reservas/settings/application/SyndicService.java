package br.com.reservas.settings.application;

import br.com.reservas.auth.application.AccountService;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.shared.audit.AuditService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** `/admin/syndics` (RF-UNI-07): usa {@link AccountService} (modulo `auth`) + auditoria (RNF-07). */
@Service
public class SyndicService {

    private final AccountService accounts;
    private final AuditService audit;

    public SyndicService(AccountService accounts, AuditService audit) {
        this.accounts = accounts;
        this.audit = audit;
    }

    public List<UserAccount> list(UUID condominiumId) {
        return accounts.listSyndics(condominiumId);
    }

    public record Created(UserAccount account, String tempPassword) {
    }

    public Created create(UUID condominiumId, UUID actorId, String name, String email, String phone) {
        String tempPassword = accounts.createSyndic(condominiumId, name, email, phone);
        UserAccount account = accounts.listSyndics(condominiumId).stream()
            .filter(a -> a.getEmail().equalsIgnoreCase(email))
            .findFirst()
            .orElseThrow();
        audit.record(actorId, "ADMIN", "SYNDIC_CREATE", "USER_ACCOUNT", account.getId(),
            Map.of("email", account.getEmail()), null);
        return new Created(account, tempPassword);
    }

    public void deactivate(UUID accountId, UUID actorId) {
        accounts.deactivate(accountId);
        audit.record(actorId, "ADMIN", "SYNDIC_DEACTIVATE", "USER_ACCOUNT", accountId, Map.of(), null);
    }
}
