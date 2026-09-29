package br.com.reservas.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Conta de login (`user_account`, V2). Uma conta {@code UNIT} = uma unidade;
 * uma conta {@code ADMIN}/{@code SYNDIC} e individual (CLAUDE.md secao 6).
 * A entidade concentra as regras de bloqueio (RN-05) e invalidacao de sessao
 * (RN-06) para nao espalhar esse conhecimento pelos servicos que a usam.
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    // RN-05: 5 tentativas erradas seguidas bloqueiam a conta por 15 minutos.
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "condominium_id", nullable = false)
    private UUID condominiumId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    private String username;

    private String email;

    @Column(name = "display_name")
    private String displayName;

    private String phone;

    @Column(name = "unit_id")
    private UUID unitId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "temp_password", nullable = false)
    private boolean tempPassword;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    protected UserAccount() {
        // JPA
    }

    public UserAccount(UUID condominiumId, Role role, String username, String email, String displayName,
        String phone, UUID unitId, String passwordHash) {
        this.condominiumId = condominiumId;
        this.role = role;
        this.username = username;
        this.email = email;
        this.displayName = displayName;
        this.phone = phone;
        this.unitId = unitId;
        this.passwordHash = passwordHash;
        this.tempPassword = true;
    }

    /** RN-05: verdadeiro enquanto a conta estiver dentro da janela de bloqueio. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    /** RN-05: registra uma tentativa invalida; a 5a grava o bloqueio de 15 minutos. */
    public void registerFailedAttempt(Instant now) {
        failedAttempts++;
        if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCK_DURATION);
        }
    }

    /** Login correto zera o contador de tentativas (RN-05). */
    public void registerSuccessfulLogin() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    /** Troca de senha pelo proprio usuario (RF-AUT-02): nunca fica temporaria. */
    public void changePassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tempPassword = false;
    }

    /** D-42: a conta ADMIN inicial do bootstrap nasce com senha definitiva, nao temporaria. */
    public void markPasswordAsPermanent() {
        this.tempPassword = false;
    }

    /** Reset administrativo (RN-03): nova senha temporaria e sessoes revogadas (RN-06). */
    public void resetToTemporaryPassword(String newPasswordHash) {
        this.passwordHash = newPasswordHash;
        this.tempPassword = true;
        this.failedAttempts = 0;
        this.lockedUntil = null;
        this.tokenVersion++;
    }

    /** RN-06: desativar a conta invalida todas as sessoes ativas. */
    public void deactivate() {
        this.active = false;
        this.tokenVersion++;
    }

    /** D-47: libera o username original ao desativar a unidade, para recadastro. */
    public void renameUsername(String newUsername) {
        this.username = newUsername;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public Role getRole() {
        return role;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhone() {
        return phone;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isTempPassword() {
        return tempPassword;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isActive() {
        return active;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }
}
