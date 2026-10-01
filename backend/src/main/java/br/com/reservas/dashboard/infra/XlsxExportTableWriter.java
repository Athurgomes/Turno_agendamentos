package br.com.reservas.dashboard.infra;

import br.com.reservas.dashboard.application.ExportTable;
import br.com.reservas.dashboard.application.ExportTableWriter;
import br.com.reservas.dashboard.domain.ExportFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * `GET /exports/{type}?format=xlsx` (F8-2, docs/03): uma planilha, cabeçalho
 * em negrito, células tipadas (número, data, texto). {@link XSSFWorkbook}
 * basta para o volume do MVP (D-61/D-62). Sanitização de fórmula (RNF-01) já
 * vem pronta em {@link ExportTable#sanitizeText(String)}; o Excel também pode
 * reavaliar texto de célula como fórmula ao abrir um XLSX, por isso o prefixo
 * `'` continua valendo aqui (defesa em profundidade).
 */
@Component
class XlsxExportTableWriter implements ExportTableWriter {

    @Override
    public ExportFormat format() {
        return ExportFormat.XLSX;
    }

    @Override
    public byte[] write(ExportTable table) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Exportação");
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle dateStyle = dateStyle(workbook, "dd/mm/yyyy");
            CellStyle timeStyle = dateStyle(workbook, "hh:mm");
            CellStyle dateTimeStyle = dateStyle(workbook, "dd/mm/yyyy hh:mm");

            Row headerRow = sheet.createRow(0);
            List<String> headers = table.headers();
            for (int col = 0; col < headers.size(); col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers.get(col));
                cell.setCellStyle(headerStyle);
            }

            List<List<Object>> rows = table.rows();
            for (int r = 0; r < rows.size(); r++) {
                Row row = sheet.createRow(r + 1);
                List<Object> values = rows.get(r);
                for (int col = 0; col < values.size(); col++) {
                    writeCell(row.createCell(col), values.get(col), dateStyle, timeStyle, dateTimeStyle);
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeCell(Cell cell, Object value, CellStyle dateStyle, CellStyle timeStyle,
        CellStyle dateTimeStyle) {
        switch (value) {
            case null -> cell.setBlank();
            case BigDecimal decimal -> cell.setCellValue(decimal.doubleValue());
            case Long number -> cell.setCellValue(number);
            case Integer number -> cell.setCellValue(number);
            case Boolean bool -> cell.setCellValue(bool ? "Sim" : "Não");
            case LocalDate date -> {
                cell.setCellValue(date);
                cell.setCellStyle(dateStyle);
            }
            case LocalTime time -> {
                cell.setCellValue(LocalDateTime.of(1899, 12, 30, time.getHour(), time.getMinute()));
                cell.setCellStyle(timeStyle);
            }
            case LocalDateTime dateTime -> {
                cell.setCellValue(dateTime);
                cell.setCellStyle(dateTimeStyle);
            }
            // RNF-01/vibe-security: mesma neutralização de fórmula do CSV, mesmo já saneado a montante.
            case String text -> cell.setCellValue(ExportTable.sanitizeText(text));
            default -> cell.setCellValue(String.valueOf(value));
        }
    }

    private CellStyle headerStyle(XSSFWorkbook workbook) {
        Font boldFont = workbook.createFont();
        boldFont.setBold(true);
        CellStyle style = workbook.createCellStyle();
        style.setFont(boldFont);
        return style;
    }

    private CellStyle dateStyle(XSSFWorkbook workbook, String format) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.getCreationHelper().createDataFormat().getFormat(format));
        return style;
    }
}
