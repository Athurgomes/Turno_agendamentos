package br.com.reservas.area.domain;

import br.com.reservas.shared.error.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Área comum (`common_area`, V5). RN-11..17: campos obrigatórios, cobrança
 * condicional, categorias fixas, status, soft delete. A checagem de "quem
 * pode editar qual campo" (D-20, SYNDIC x ADMIN) fica no {@code application}
 * (o domínio só garante os invariantes da própria entidade).
 */
@Entity
@Table(name = "common_area")
public class Area {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "condominium_id", nullable = false)
    private UUID condominiumId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AreaCategory category;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String rules;

    @Column(name = "conduct_guidelines", nullable = false)
    private String conductGuidelines;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AreaStatus status = AreaStatus.ACTIVE;

    @Column(name = "requires_payment", nullable = false)
    private boolean requiresPayment;

    private BigDecimal price;

    @Column(name = "payment_whatsapp")
    private String paymentWhatsapp;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Version
    private int version;

    protected Area() {
        // JPA
    }

    public Area(UUID condominiumId, String name, AreaCategory category, String description, String rules,
        String conductGuidelines, int capacity, boolean requiresPayment, BigDecimal price, String paymentWhatsapp) {
        this.condominiumId = condominiumId;
        applyFields(name, category, description, rules, conductGuidelines, capacity, requiresPayment, price,
            paymentWhatsapp);
    }

    /** RN-11/RN-12: campos do cadastro/edição, com o mesmo invariante de cobrança em ambos os casos. */
    public void applyFields(String name, AreaCategory category, String description, String rules,
        String conductGuidelines, int capacity, boolean requiresPayment, BigDecimal price, String paymentWhatsapp) {
        if (capacity <= 0) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.UNPROCESSABLE_ENTITY,
                "A capacidade precisa ser maior que zero.");
        }
        if (requiresPayment && (price == null || price.signum() <= 0 || paymentWhatsapp == null
            || paymentWhatsapp.isBlank())) {
            throw new BusinessException("PAYMENT_INFO_REQUIRED", HttpStatus.UNPROCESSABLE_ENTITY,
                "Área paga precisa de valor maior que zero e WhatsApp da administração.");
        }
        this.name = name;
        this.category = category;
        this.description = description;
        this.rules = rules;
        this.conductGuidelines = conductGuidelines;
        this.capacity = capacity;
        this.requiresPayment = requiresPayment;
        // RN-12: desmarcar cobrança sempre limpa valor/whatsapp (não fica lixo de uma cobrança antiga).
        this.price = requiresPayment ? price : null;
        this.paymentWhatsapp = requiresPayment ? paymentWhatsapp : null;
    }

    /** RN-14: so ADMIN chama (checado no service); nenhuma transicao e proibida no MVP. */
    public void changeStatus(AreaStatus status) {
        this.status = status;
    }

    /** RN-15: soft delete. */
    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.status = AreaStatus.INACTIVE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCondominiumId() {
        return condominiumId;
    }

    public String getName() {
        return name;
    }

    public AreaCategory getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getRules() {
        return rules;
    }

    public String getConductGuidelines() {
        return conductGuidelines;
    }

    public int getCapacity() {
        return capacity;
    }

    public AreaStatus getStatus() {
        return status;
    }

    public boolean isRequiresPayment() {
        return requiresPayment;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getPaymentWhatsapp() {
        return paymentWhatsapp;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public int getVersion() {
        return version;
    }
}
