package br.com.reservas.settings.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** `condominium` (V1): D-13, uma unica linha em operacao no MVP. */
@Entity
@Table(name = "condominium")
public class Condominium {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String timezone;

    @Column(name = "default_payment_whatsapp")
    private String defaultPaymentWhatsapp;

    protected Condominium() {
        // JPA
    }

    /** RF-UNI-07/D-43: nome e WhatsApp padrao sao editados por `PUT /admin/settings`; fuso e so leitura (D-15). */
    public void updateContactInfo(String name, String defaultPaymentWhatsapp) {
        this.name = name;
        this.defaultPaymentWhatsapp = defaultPaymentWhatsapp;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getDefaultPaymentWhatsapp() {
        return defaultPaymentWhatsapp;
    }
}
