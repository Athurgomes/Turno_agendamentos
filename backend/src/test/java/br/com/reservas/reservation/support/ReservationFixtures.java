package br.com.reservas.reservation.support;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Setup de banco reutilizado pelos testes de integração de `reservation`:
 * condomínio + motor de regras default (RN-18..24), unidade e morador
 * principal. Área é criada via API de verdade (multipart, com horário de
 * funcionamento) em cada teste, não aqui — é o único jeito de obter o
 * `AreaBookingInfo` completo que a política usa.
 */
public final class ReservationFixtures {

    private ReservationFixtures() {
    }

    /** Condomínio + `condominium_settings` com os defaults da RN-18..24 (V1). */
    public static UUID insertCondominium(JdbcTemplate jdbcTemplate, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name, timezone) values (?, ?, 'America/Sao_Paulo')", id,
            name);
        jdbcTemplate.update("insert into condominium_settings (condominium_id) values (?)", id);
        return id;
    }

    public static UUID insertUnit(JdbcTemplate jdbcTemplate, UUID condominiumId, String identifier) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("insert into unit (id, condominium_id, number, identifier) values (?, ?, ?, ?)", id,
            condominiumId, identifier, identifier);
        return id;
    }

    public static UUID insertResident(JdbcTemplate jdbcTemplate, UUID unitId, String name) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace(" ", ".") + "." + id.toString().substring(0, 8);
        jdbcTemplate.update(
            "insert into resident (id, unit_id, name, phone, email, cpf, is_primary) values (?, ?, ?, ?, ?, ?, true)",
            id, unitId, name, "5562999990000", slug + "@exemplo.test", "12345678901");
        return id;
    }
}
