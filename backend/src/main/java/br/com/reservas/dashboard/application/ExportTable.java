package br.com.reservas.dashboard.application;

import java.util.List;

/**
 * Resultado independente de formato de {@link DashboardExportService}: cabeçalhos
 * (pt-BR, já na ordem do contrato) e linhas de células tipadas. Cada célula é
 * {@code null}, {@link String}, {@link java.math.BigDecimal}, {@link Long},
 * {@link java.time.LocalDate}, {@link java.time.LocalTime} ou
 * {@link java.time.LocalDateTime} — os dois escritores (CSV/XLSX,
 * `dashboard.infra`) sabem formatar cada um desses tipos. Texto vindo de dado
 * do usuário (nomes, motivos) é neutralizado contra injeção de fórmula
 * (RNF-01/vibe-security) por {@link #sanitizeText(String)} antes de entrar na
 * tabela — os escritores não precisam repetir a checagem.
 */
public record ExportTable(List<String> headers, List<List<Object>> rows) {

    private static final String DANGEROUS_PREFIXES = "=+-@\t\r";

    /** Prefixa com `'` texto que começa com `=`, `+`, `-`, `@`, tab ou CR, para o Excel nunca o interpretar como fórmula. */
    public static String sanitizeText(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        return DANGEROUS_PREFIXES.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
    }
}
