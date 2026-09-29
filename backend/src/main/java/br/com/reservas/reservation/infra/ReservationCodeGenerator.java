package br.com.reservas.reservation.infra;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** `code = RES-{ano local}-{nextval('reservation_code_seq') 6 dígitos}` (V6, RF-RES-03). */
@Component
public class ReservationCodeGenerator {

    private final JdbcTemplate jdbcTemplate;

    public ReservationCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String next(int year) {
        Long sequence = jdbcTemplate.queryForObject("select nextval('reservation_code_seq')", Long.class);
        return "RES-%d-%06d".formatted(year, sequence);
    }
}
