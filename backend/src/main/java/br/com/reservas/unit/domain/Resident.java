package br.com.reservas.unit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Morador (`resident`, V3): pessoa sem login proprio, vinculada a uma
 * unidade (RN-07). O CPF e digitos-only (validado em {@link Cpf} antes de
 * chegar aqui); e-mail/CPF sao obrigatorios so para o principal.
 */
@Entity
@Table(name = "resident")
public class Resident {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    private String email;

    // cpf e char(11) no banco (V3); JdbcTypeCode.CHAR evita falha de schema-validation
    // do Hibernate (que por padrao mapeia String para varchar).
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(length = 11)
    private String cpf;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Resident() {
        // JPA
    }

    public Resident(UUID unitId, String name, String phone, String email, String cpf, boolean primary) {
        this.unitId = unitId;
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.cpf = cpf;
        this.primary = primary;
    }

    /** RN-09: conta UNIT (e ADMIN) podem editar nome/telefone/e-mail; CPF nunca por aqui. */
    public void updateContact(String name, String phone, String email) {
        this.name = name;
        this.phone = phone;
        this.email = email;
    }

    /** RF-UNI-03: so o ADMIN chama (troca de principal via PUT /units/{id}). */
    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    /** RN-09: so o ADMIN chama (a conta UNIT nunca edita CPF, nem do proprio principal). */
    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUnitId() {
        return unitId;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public String getEmail() {
        return email;
    }

    public String getCpf() {
        return cpf;
    }

    public boolean isPrimary() {
        return primary;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
