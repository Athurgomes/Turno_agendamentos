package br.com.reservas.dashboard.infra;

import java.math.BigDecimal;

/** Linha agregada de reservas `BOOKING` no período (D-61: 1 consulta, `FILTER`). */
public record ReservationTotalsRow(long total, long pendingPayment, long confirmed, long cancelled,
    long cancelledByResident, long cancelledByAdmin, long cancelledBySystem, BigDecimal amountConfirmed,
    BigDecimal amountPending) {
}
