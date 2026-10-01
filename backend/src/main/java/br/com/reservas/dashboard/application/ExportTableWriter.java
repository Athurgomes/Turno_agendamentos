package br.com.reservas.dashboard.application;

import br.com.reservas.dashboard.domain.ExportFormat;

/**
 * Escreve uma {@link ExportTable} em bytes num formato concreto (F8-2). Duas
 * implementações em `dashboard.infra`: {@code CsvExportTableWriter} e
 * {@code XlsxExportTableWriter}, escolhidas por {@link #format()}.
 */
public interface ExportTableWriter {

    ExportFormat format();

    byte[] write(ExportTable table);
}
