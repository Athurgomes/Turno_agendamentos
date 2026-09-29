package br.com.reservas.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** RN-26: link de pagamento via WhatsApp. */
class WhatsAppPaymentLinkTest {

    @Test
    @DisplayName("RN-26: monta https://wa.me/{numero}?text= com a mensagem exata da regra")
    void buildsExpectedMessage() {
        String url = WhatsAppPaymentLink.build("5562999998888", "Ana Souza", "a-1", "Salão de Festas",
            LocalDate.of(2026, 10, 15), LocalTime.of(19, 0), LocalTime.of(23, 0), "RES-2026-000001",
            new BigDecimal("150.00"));

        assertThat(url).startsWith("https://wa.me/5562999998888?text=");
        String encodedMessage = url.substring(url.indexOf("text=") + "text=".length());
        String decoded = URLDecoder.decode(encodedMessage, StandardCharsets.UTF_8);

        assertThat(decoded).isEqualTo("Olá! Sou Ana Souza, da unidade A-1. Acabei de reservar a área Salão de "
            + "Festas para o dia 15/10/2026, das 19:00 às 23:00 (protocolo RES-2026-000001). Gostaria de "
            + "realizar o pagamento de R$ 150,00 para confirmar meu agendamento.");
    }

    @Test
    @DisplayName("RN-26: espaço é codificado como %20 (não como +)")
    void encodesSpacesAsPercentTwentyNotPlus() {
        String url = WhatsAppPaymentLink.build("5562999998888", "Ana Souza", "a-1", "Salão de Festas",
            LocalDate.of(2026, 10, 15), LocalTime.of(19, 0), LocalTime.of(23, 0), "RES-2026-000001",
            new BigDecimal("150.00"));

        String encodedMessage = url.substring(url.indexOf("text=") + "text=".length());
        assertThat(encodedMessage).contains("%20").doesNotContain("+");
    }

    @Test
    @DisplayName("RN-26: identificador da unidade sai em maiúsculas na mensagem")
    void uppercasesUnitIdentifier() {
        String url = WhatsAppPaymentLink.build("5562999998888", "Ana Souza", "b-12", "Quadra",
            LocalDate.of(2026, 1, 1), LocalTime.of(8, 0), LocalTime.of(9, 0), "RES-2026-000002",
            new BigDecimal("50.00"));

        String decoded = URLDecoder.decode(url.substring(url.indexOf("text=") + 5), StandardCharsets.UTF_8);
        assertThat(decoded).contains("unidade B-12");
    }
}
