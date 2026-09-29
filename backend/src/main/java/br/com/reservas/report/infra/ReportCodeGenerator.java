package br.com.reservas.report.infra;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** `code = OCR-{ano local}-{nextval('report_code_seq') 6 dígitos}` (V7, RF-REP-01). */
@Component
public class ReportCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public ReportCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(int year) {
        Long sequence = jdbcTemplate.queryForObject("select nextval('report_code_seq')", Long.class);
        return "OCR-%d-%06d".formatted(year, sequence);
    }
}
