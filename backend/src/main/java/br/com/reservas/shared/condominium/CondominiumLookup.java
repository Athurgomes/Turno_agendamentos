package br.com.reservas.shared.condominium;

import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * D-13: o MVP roda com um unico condominio por instancia (modelo preparado
 * para varios, mas so um em operacao). Enquanto o modulo {@code settings} nao
 * existe (ele passara a ser dono da entidade {@code Condominium}), este
 * componente resolve id/fuso horario direto da tabela via JDBC (sem entidade
 * JPA), evitando duas classes mapeando a mesma tabela mais tarde.
 */
@Component
public class CondominiumLookup {

    private static final String DEFAULT_TIMEZONE = "America/Sao_Paulo";

    private final JdbcTemplate jdbcTemplate;

    public CondominiumLookup(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Id do unico condominio cadastrado, se ja existir. Desde F1-2/D-42 o
     * bootstrap da aplicacao ja garante essa linha na subida; a ordenacao por
     * {@code created_at DESC} so importa em cenarios de teste que inserem um
     * segundo condominio depois do bootstrap (o modelo de producao tem sempre
     * exatamente uma linha, D-13).
     */
    public Optional<UUID> currentId() {
        return queryOne("SELECT id FROM condominium ORDER BY created_at DESC LIMIT 1",
            (rs, i) -> (UUID) rs.getObject("id"));
    }

    /** Fuso horario do condominio, ou {@code America/Sao_Paulo} se nenhum existir ainda. */
    public String currentTimezone() {
        return queryOne("SELECT timezone FROM condominium ORDER BY created_at DESC LIMIT 1",
            (rs, i) -> rs.getString("timezone")).orElse(DEFAULT_TIMEZONE);
    }

    private <T> Optional<T> queryOne(String sql, org.springframework.jdbc.core.RowMapper<T> mapper) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, mapper));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
