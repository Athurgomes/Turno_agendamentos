package br.com.reservas.area.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import br.com.reservas.shared.error.BusinessException;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-11/RN-12: campos obrigatorios e cobranca condicional. */
class AreaTest {

    private final UUID condominiumId = UUID.randomUUID();

    @Test
    @DisplayName("RN-12: area paga sem valor -> PAYMENT_INFO_REQUIRED")
    void payingAreaWithoutPriceIsRejected() {
        assertThatThrownBy(() -> new Area(condominiumId, "Salao", AreaCategory.PARTY_ROOM, "d", "r", "c", 10, true,
            null, "5562999990000"))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("PAYMENT_INFO_REQUIRED"));
    }

    @Test
    @DisplayName("RN-12: area paga sem WhatsApp -> PAYMENT_INFO_REQUIRED")
    void payingAreaWithoutWhatsappIsRejected() {
        assertThatThrownBy(() -> new Area(condominiumId, "Salao", AreaCategory.PARTY_ROOM, "d", "r", "c", 10, true,
            BigDecimal.TEN, null))
            .isInstanceOf(BusinessException.class)
            .satisfies(ex -> assertThat(((BusinessException) ex).code()).isEqualTo("PAYMENT_INFO_REQUIRED"));
    }

    @Test
    @DisplayName("RN-12: desmarcar cobranca limpa valor e WhatsApp gravados")
    void unmarkingPaymentClearsPriceAndWhatsapp() {
        Area area = new Area(condominiumId, "Salao", AreaCategory.PARTY_ROOM, "d", "r", "c", 10, true,
            BigDecimal.TEN, "5562999990000");

        area.applyFields("Salao", AreaCategory.PARTY_ROOM, "d", "r", "c", 10, false, null, null);

        assertThat(area.getPrice()).isNull();
        assertThat(area.getPaymentWhatsapp()).isNull();
    }

    @Test
    @DisplayName("RN-11: capacidade zero ou negativa e rejeitada")
    void nonPositiveCapacityIsRejected() {
        assertThatThrownBy(() -> new Area(condominiumId, "Salao", AreaCategory.PARTY_ROOM, "d", "r", "c", 0, false,
            null, null))
            .isInstanceOf(BusinessException.class);
    }
}
