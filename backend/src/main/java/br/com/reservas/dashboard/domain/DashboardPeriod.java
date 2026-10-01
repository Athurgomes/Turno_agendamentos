package br.com.reservas.dashboard.domain;

import br.com.reservas.shared.error.BusinessException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpStatus;

/**
 * Período de filtro dos indicadores do dashboard (RF-DAS-01, docs/03 "Dashboard
 * e exportação"): datas `from`/`to` inclusivas, no fuso do condomínio. Value
 * object reaproveitado pela exportação (F8-2) — a mesma regra de resolução e
 * validação de período vale para os dois.
 */
public record DashboardPeriod(LocalDate from, LocalDate to) {

    private static final long MAX_DAYS = 366;

    /**
     * `from`/`to` ausentes (os dois) → mês corrente de {@code today} (já
     * calculado pelo chamador no fuso do condomínio, via {@code Clock}). Um só
     * dos dois ausente, `from > to` ou intervalo > 366 dias → 400 VALIDATION_ERROR.
     */
    public static DashboardPeriod resolve(LocalDate from, LocalDate to, LocalDate today) {
        if (from == null && to == null) {
            return new DashboardPeriod(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()));
        }
        if (from == null || to == null || to.isBefore(from) || ChronoUnit.DAYS.between(from, to) > MAX_DAYS) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "O período informado é inválido: informe `from` e `to`, com `from` <= `to` e "
                    + "intervalo de no máximo 366 dias.");
        }
        return new DashboardPeriod(from, to);
    }
}
