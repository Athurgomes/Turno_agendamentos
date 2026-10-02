package br.com.reservas.dashboard.infra;

import br.com.reservas.dashboard.application.ExportTable;
import br.com.reservas.dashboard.application.ExportTableWriter;
import br.com.reservas.dashboard.domain.ExportFormat;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * `GET /exports/{type}?format=csv` (F8-2, docs/03): UTF-8 com BOM, separador
 * `;`, decimal com vírgula, datas `dd/MM/yyyy` e horas `HH:mm` — abre direto
 * no Excel em pt-BR. Sanitização de fórmula (RNF-01) já vem pronta em
 * {@link ExportTable#sanitizeText(String)}; aqui só falta escapar `;`/aspas/
 * quebra de linha (RFC 4180).
 */
@Component
class CsvExportTableWriter implements ExportTableWriter {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Override
    public ExportFormat format() {
        return ExportFormat.CSV;
    }

    @Override
    public byte[] write(ExportTable table) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(BOM);
        out.writeBytes(line(table.headers().stream().map(this::cell).toList()));
        for (List<Object> row : table.rows()) {
            out.writeBytes(line(row.stream().map(this::cellValue).map(this::cell).toList()));
        }
        return out.toByteArray();
    }

    private byte[] line(List<String> cells) {
        return (String.join(";", cells) + "\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private String cellValue(Object value) {
        return switch (value) {
            case null -> "";
            case BigDecimal decimal -> decimal.toPlainString().replace('.', ',');
            case LocalDate date -> date.format(DATE);
            case LocalTime time -> time.format(TIME);
            case LocalDateTime dateTime -> dateTime.format(DATE_TIME);
            case Boolean bool -> bool ? "Sim" : "Não";
            default -> String.valueOf(value);
        };
    }

    // RFC 4180: valor com `;`, aspas ou quebra de linha vai entre aspas (aspas internas dobradas).
    private String cell(String value) {
        if (value.indexOf(';') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }
}
