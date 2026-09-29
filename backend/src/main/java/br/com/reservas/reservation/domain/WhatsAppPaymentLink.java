package br.com.reservas.reservation.domain;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * RN-26: link de pagamento via WhatsApp (`https://wa.me/{numero}?text={mensagem}`)
 * com a mensagem fixa da regra. Função pura, sem Spring: {@link URLEncoder}
 * codifica espaço como {@code +} (padrão {@code application/x-www-form-urlencoded});
 * a RN pede {@code %20}, então trocamos na saída.
 */
public final class WhatsAppPaymentLink {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private WhatsAppPaymentLink() {
    }

    public static String build(String paymentWhatsapp, String residentName, String unitIdentifier, String areaName,
        LocalDate date, LocalTime startTime, LocalTime endTime, String code, BigDecimal price) {
        String message = "Olá! Sou " + residentName + ", da unidade " + unitIdentifier.toUpperCase()
            + ". Acabei de reservar a área " + areaName + " para o dia " + date.format(DATE_FORMAT) + ", das "
            + startTime.format(TIME_FORMAT) + " às " + endTime.format(TIME_FORMAT) + " (protocolo " + code
            + "). Gostaria de realizar o pagamento de R$ " + formatPrice(price)
            + " para confirmar meu agendamento.";
        String encoded = URLEncoder.encode(message, StandardCharsets.UTF_8).replace("+", "%20");
        return "https://wa.me/" + paymentWhatsapp + "?text=" + encoded;
    }

    private static String formatPrice(BigDecimal price) {
        return price.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',');
    }
}
