package br.com.reservas.unit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Unidade do condominio (`unit`, V3). Bloco e numero nao sao editaveis
 * (D-43): para corrigi-los, desativa-se e recadastra-se (RN-02).
 */
@Entity
@Table(name = "unit")
public class Unit {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "condominium_id", nullable = false)
    private UUID condominiumId;

    private String block;

    @Column(nullable = false)
    private String number;

    @Column(nullable = false)
    private String identifier;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Unit() {
        // JPA
    }

    public Unit(UUID condominiumId, String block, String number, String identifier) {
        this.condominiumId = condominiumId;
        this.block = block;
        this.number = number;
        this.identifier = identifier;
    }

    /** D-47: desativa a unidade (RN-10); o identificador fica livre para recadastro (unicidade parcial, V3). */
    public void deactivate(Instant now) {
        this.active = false;
        this.deletedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getBlock() {
        return block;
    }

    public String getNumber() {
        return number;
    }

    public String getIdentifier() {
        return identifier;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
