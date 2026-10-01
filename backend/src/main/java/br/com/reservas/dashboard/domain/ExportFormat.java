package br.com.reservas.dashboard.domain;

import br.com.reservas.shared.error.BusinessException;
import java.util.Locale;
import org.springframework.http.HttpStatus;

/** `format` de `GET /exports/{type}` (F8-2): `csv` ou `xlsx`; outro valor -&gt; 400 VALIDATION_ERROR. */
public enum ExportFormat {
    CSV("text/csv;charset=UTF-8", "csv"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public static ExportFormat fromParam(String value) {
        try {
            return ExportFormat.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BusinessException("VALIDATION_ERROR", HttpStatus.BAD_REQUEST,
                "Formato de exportação inválido. Use csv ou xlsx.");
        }
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }
}
