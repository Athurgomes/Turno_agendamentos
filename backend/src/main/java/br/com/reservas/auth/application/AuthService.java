package br.com.reservas.auth.application;

import br.com.reservas.auth.domain.PasswordPolicy;
import br.com.reservas.auth.domain.RefreshToken;
import br.com.reservas.auth.domain.Role;
import br.com.reservas.auth.domain.UserAccount;
import br.com.reservas.auth.infra.RefreshTokenRepository;
import br.com.reservas.auth.infra.UserAccountRepository;
import br.com.reservas.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Caso de uso central do módulo `auth`: login, refresh (com rotação),
 * logout, troca de senha e leitura da sessão (RF-AUT-01..05, RN-04..06,
 * D-42). Interface pequena (5 métodos) usada só pelo {@code AuthController}.
 */
@Service
public class AuthService {

    // D-42: refresh de 7 dias, rotacionado a cada uso.
    static final Duration REFRESH_TTL = Duration.ofDays(7);

    // BCrypt de uma senha que nunca será usada; gasta o mesmo tempo de hash de
    // uma comparação real para não revelar por tempo se a conta existe (RN-05).
    private static final String DUMMY_HASH =
        "$2a$10$7EqJtq98hPqEX7fNZaFWoOhi5.zaVJ7hL8u/dV6a4x/6Bq6y6w3.G";

    private final UserAccountRepository accounts;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtIssuer jwtIssuer;
    private final LoginRateLimiter rateLimiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserAccountRepository accounts, RefreshTokenRepository refreshTokens,
        PasswordEncoder passwordEncoder, JwtIssuer jwtIssuer, LoginRateLimiter rateLimiter, Clock clock) {
        this.accounts = accounts;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtIssuer = jwtIssuer;
        this.rateLimiter = rateLimiter;
        this.clock = clock;
    }

    public AuthResult login(String login, String password, String clientIp) {
        if (!rateLimiter.tryAcquire(clientIp)) {
            throw new BusinessException("TOO_MANY_REQUESTS", HttpStatus.TOO_MANY_REQUESTS,
                "Muitas tentativas de login. Aguarde um minuto e tente novamente.");
        }

        Instant now = Instant.now(clock);
        Optional<UserAccount> found = accounts.findByLogin(login);

        if (found.isEmpty() || !found.get().isActive()) {
            // RN-05: mesma resposta e mesmo custo de BCrypt de uma tentativa com
            // conta existente, para não permitir enumerar contas por tempo de resposta.
            passwordEncoder.matches(password, DUMMY_HASH);
            throw invalidCredentials();
        }

        UserAccount account = found.get();
        if (account.isLocked(now)) {
            throw new BusinessException("ACCOUNT_LOCKED", HttpStatus.LOCKED,
                "Conta temporariamente bloqueada por excesso de tentativas. Tente novamente mais tarde.");
        }

        if (!passwordEncoder.matches(password, account.getPasswordHash())) {
            account.registerFailedAttempt(now);
            accounts.save(account);
            throw invalidCredentials();
        }

        account.registerSuccessfulLogin();
        accounts.save(account);
        return issueSession(account, now);
    }

    public AuthResult refresh(String rawRefreshToken) {
        Instant now = Instant.now(clock);
        RefreshToken current = findValidOrThrow(rawRefreshToken, now);

        UserAccount account = accounts.findById(current.getUserId())
            .filter(UserAccount::isActive)
            .orElseThrow(this::unauthenticated);

        current.revoke(now);
        refreshTokens.save(current);
        return issueSession(account, now);
    }

    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        Instant now = Instant.now(clock);
        refreshTokens.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.revoke(now);
                refreshTokens.save(token);
            }
        });
    }

    public void changePassword(UUID accountId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findById(accountId).orElseThrow(this::unauthenticated);

        if (!passwordEncoder.matches(currentPassword, account.getPasswordHash())) {
            throw new BusinessException("INVALID_CURRENT_PASSWORD", HttpStatus.UNPROCESSABLE_ENTITY,
                "Senha atual incorreta.");
        }

        PasswordPolicy.validate(newPassword, currentPassword, account.getUsername(), account.getEmail());

        account.changePassword(passwordEncoder.encode(newPassword));
        accounts.save(account);
        revokeAllRefreshTokens(accountId, Instant.now(clock));
    }

    public SessionUser me(UUID accountId) {
        UserAccount account = accounts.findById(accountId).orElseThrow(this::unauthenticated);
        return toSessionUser(account);
    }

    private AuthResult issueSession(UserAccount account, Instant now) {
        String rawRefreshToken = newRawToken();
        RefreshToken refreshToken = new RefreshToken(account.getId(), hash(rawRefreshToken), now.plus(REFRESH_TTL));
        refreshTokens.save(refreshToken);

        String accessToken = jwtIssuer.issue(account);
        return new AuthResult(accessToken, jwtIssuer.accessTtlSeconds(), rawRefreshToken, toSessionUser(account));
    }

    private RefreshToken findValidOrThrow(String rawRefreshToken, Instant now) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw unauthenticated();
        }
        RefreshToken token = refreshTokens.findByTokenHash(hash(rawRefreshToken)).orElseThrow(this::unauthenticated);

        if (token.getRevokedAt() != null) {
            // Reuso de um refresh token já revogado: possível roubo (D-42).
            // Revoga todas as sessões da conta por segurança.
            revokeAllRefreshTokens(token.getUserId(), now);
            throw unauthenticated();
        }
        if (!token.isValid(now)) {
            throw unauthenticated();
        }
        return token;
    }

    private void revokeAllRefreshTokens(UUID accountId, Instant now) {
        List<RefreshToken> active = refreshTokens.findByUserIdAndRevokedAtIsNull(accountId);
        active.forEach(token -> token.revoke(now));
        refreshTokens.saveAll(active);
    }

    private SessionUser toSessionUser(UserAccount account) {
        boolean isUnit = account.getRole() == Role.UNIT;
        // F1: ainda não existe o módulo de unidades; usa o username como
        // identificador provisório (D-42, ligado à unidade de verdade na F2).
        String name = isUnit ? "Unidade " + account.getUsername() : account.getDisplayName();
        String unitIdentifier = isUnit ? account.getUsername() : null;
        return new SessionUser(account.getId(), account.getRole(), name, account.getUnitId(), unitIdentifier,
            account.isTempPassword());
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED,
            "Usuário ou senha inválidos.");
    }

    private BusinessException unauthenticated() {
        return new BusinessException("UNAUTHENTICATED", HttpStatus.UNAUTHORIZED,
            "Sessão inválida ou expirada. Faça login novamente.");
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }
}
