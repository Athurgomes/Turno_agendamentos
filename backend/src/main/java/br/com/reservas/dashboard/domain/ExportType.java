package br.com.reservas.dashboard.domain;

import br.com.reservas.shared.error.BusinessException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * `type` de `GET /exports/{type}` (F8-2, docs/03 "Dashboard e exportação"):
 * um valor por planilha suportada. `type` fora dessa lista -&gt; 400
 * VALIDATION_ERROR.
 */
public enum ExportType {
    RESERVATIONS,
    AREAS,
    REPORTS,
    PAYMENTS,
    UNITS;

    public static ExportType fromParam(String value) {
        try {
            return ExportType.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "Tipo de exportação inválido. Use reservations, areas, reports, payments ou units.");
        }
    }

    public String param() {
        return name().toLowerCase(Locale.ROOT);
    }
}
