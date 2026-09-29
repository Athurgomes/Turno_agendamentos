package br.com.reservas.shared.condominium;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.reservas.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** D-13: um condominio operacional por instancia; RNF-10 usa o fuso dele. */
@SpringBootTest
class CondominiumLookupTest extends AbstractIntegrationTest {

    @Autowired
    private CondominiumLookup lookup;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Transactional
    @DisplayName("D-13: sem condominio cadastrado, currentTimezone() usa o default America/Sao_Paulo")
    void defaultsTimezoneWhenNoCondominiumExists() {
        assertThat(lookup.currentTimezone()).isEqualTo("America/Sao_Paulo");
    }

    @Test
    @Transactional
    @DisplayName("D-13: com condominio cadastrado, currentId()/currentTimezone() retornam os dados dele")
    void resolvesExistingCondominium() {
        UUID condominiumId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO condominium (id, name, timezone) VALUES (?, ?, ?)",
            condominiumId, "Condominio Lookup", "America/Bahia");

        assertThat(lookup.currentId()).contains(condominiumId);
        assertThat(lookup.currentTimezone()).isEqualTo("America/Bahia");
    }
}
