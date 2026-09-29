package br.com.reservas.auth.application;

import br.com.reservas.auth.domain.RefreshToken;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.TemporaryPasswordGenerator;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.RefreshTokenRepository;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.shared.error.BusinessException;
import jakarta.persistence.EntityNotFoundException;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Interface pública do módulo `auth` para os demais módulos criarem e
 * gerenciarem contas (F2: unidades e síndicos), sem conhecer entidade JPA,
 * hash de senha ou gerador de senha temporária.
 */
@Service
public class AccountService {

    private final UserAccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final Clock clock;

    public AccountService(UserAccountRepository accounts, RefreshTokenRepository refreshTokens,
        PasswordEncoder passwordEncoder, TemporaryPasswordGenerator passwordGenerator, Clock clock) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.clock = clock;
    }

    /** RN-02/RN-03: cria a conta UNIT com senha temporária; devolve o texto plano (exibido uma única vez). */
    public String createUnitAccount(UUID condominiumId, UUID unitId, String username) {
        String tempPassword = passwordGenerator.generate();
        UserAccount account = new UserAccount(condominiumId, Role.UNIT, username.toLowerCase(), null, null, null,
            unitId, passwordEncoder.encode(tempPassword));
        accounts.save(account);
        return tempPassword;
    }

    /** RF-UNI-07: cria a conta do síndico (login por e-mail) com senha temporária; e-mail em uso -&gt; EMAIL_TAKEN. */
    public String createSyndic(UUID condominiumId, String name, String email, String phone) {
        if (accounts.existsByEmailIgnoreCase(email)) {
            throw new BusinessException("EMAIL_TAKEN", HttpStatus.CONFLICT,
                "Já existe uma conta com este e-mail.");
        }
        String tempPassword = passwordGenerator.generate();
        UserAccount account = new UserAccount(condominiumId, Role.SYNDIC, null, email.toLowerCase(), name, phone,
            null, passwordEncoder.encode(tempPassword));
        accounts.save(account);
        return tempPassword;
    }

    /** F2-7: lista as contas de síndico do condomínio (ADMIN), por nome. */
    public List<UserAccount> listSyndics(UUID condominiumId) {
        return accounts.findByCondominiumIdAndRoleOrderByDisplayNameAsc(condominiumId, Role.SYNDIC);
    }

    /** F2: id da conta UNIT ligada à unidade, se existir. */
    public Optional<UUID> findAccountIdByUnit(UUID unitId) {
        return accounts.findByUnitId(unitId).map(UserAccount::getId);
    }

    /** Seed local (FD-1): id da conta ADMIN do condomínio, usada como ator do seed. */
    public Optional<UUID> findAdminId(UUID condominiumId) {
        return accounts.findByCondominiumIdAndRoleOrderByDisplayNameAsc(condominiumId, Role.ADMIN).stream()
            .findFirst()
            .map(UserAccount::getId);
    }

    /**
     * Seed local (FD-1): define uma senha definitiva conhecida (não temporária) para uma conta
     * de demonstração. Não exposto por nenhuma rota da API; existe só para o seed poder deixar
     * as contas fictícias prontas para uso sem a etapa de troca de senha temporária.
     */
    public void setPassword(UUID accountId, String rawPassword) {
        UserAccount account = findOrThrow(accountId);
        account.changePassword(passwordEncoder.encode(rawPassword));
        accounts.save(account);
    }

    /**
     * Seed demo (D-59): mesmo efeito de {@link #resetPassword} (senha
     * temporária, {@code tempPassword = true}), mas com um valor conhecido
     * (não gerado), para a conta `a-102` do cenário de demonstração poder
     * mostrar o banner de primeiro acesso com uma senha fixa vinda de
     * variável de ambiente (`APP_DEMO_TEMP_PASSWORD`), em vez de uma senha
     * temporária aleatória que só o seed conheceria. Não exposto por nenhuma
     * rota da API.
     */
    public void setTemporaryPassword(UUID accountId, String rawPassword) {
        UserAccount account = findOrThrow(accountId);
        account.resetToTemporaryPassword(passwordEncoder.encode(rawPassword));
        accounts.save(account);
    }

    /** F2: username (login) da conta UNIT ligada à unidade, para `UnitDetail.username`. */
    public Optional<String> usernameByUnit(UUID unitId) {
        return accounts.findByUnitId(unitId).map(UserAccount::getUsername);
    }

    /**
     * F3: nome de exibição de uma conta ADMIN/SYNDIC para DTOs como
     * `uploadedBy`/`author` (docs/03): "Administração" para ADMIN, o
     * `display_name` para SYNDIC. Usado por outros módulos (area, e depois
     * report) que só guardam o id do ator.
     */
    public String displayNameFor(UUID accountId) {
        UserAccount account = findOrThrow(accountId);
        return account.getRole() == Role.ADMIN ? "Administração" : account.getDisplayName();
    }

    /** D-47: desativa a conta da unidade e libera o username original para recadastro. */
    public void deactivateUnitAccount(UUID accountId, String newUsername) {
        UserAccount account = findOrThrow(accountId);
        account.renameUsername(newUsername.toLowerCase());
        account.deactivate();
        accounts.save(account);
        revokeAllRefreshTokens(accountId);
    }

    /** RN-03: gera nova senha temporária e revoga as sessões ativas (RN-06). */
    public String resetPassword(UUID accountId) {
        UserAccount account = findOrThrow(accountId);
        String tempPassword = passwordGenerator.generate();
        account.resetToTemporaryPassword(passwordEncoder.encode(tempPassword));
        accounts.save(account);
        revokeAllRefreshTokens(accountId);
        return tempPassword;
    }

    /** RN-06: desativa a conta e invalida toda sessão ativa. */
    public void deactivate(UUID accountId) {
        UserAccount account = findOrThrow(accountId);
        account.deactivate();
        accounts.save(account);
        revokeAllRefreshTokens(accountId);
    }

    /** F4-5: dados mínimos de várias contas de uma vez (ator de eventos de reserva, evita N+1). */
    public Map<UUID, AccountSummary> summaries(Collection<UUID> accountIds) {
        if (accountIds.isEmpty()) {
            return Map.of();
        }
        return accounts.findAllById(accountIds).stream()
            .collect(Collectors.toMap(UserAccount::getId,
                a -> new AccountSummary(a.getId(), a.getRole(), a.getDisplayName(), a.getUnitId())));
    }

    private UserAccount findOrThrow(UUID accountId) {
        return accounts.findById(accountId)
            .orElseThrow(() -> new EntityNotFoundException("Conta não encontrada: " + accountId));
    }

    private void revokeAllRefreshTokens(UUID accountId) {
        Instant now = Instant.now(clock);
        List<RefreshToken> active = refreshTokens.findByUserIdAndRevokedAtIsNull(accountId);
        active.forEach(token -> token.revoke(now));
        refreshTokens.saveAll(active);
    }
}
