package br.com.reservas.dashboard.infra;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Uma linha de `GET /exports/payments` (F8-2, docs/03): reservas com cobrança no período. */
public record PaymentExportRow(String code, String areaName, String unitIdentifier, LocalDate date,
    BigDecimal amount, String status, LocalDateTime paymentConfirmedAt) {
}
