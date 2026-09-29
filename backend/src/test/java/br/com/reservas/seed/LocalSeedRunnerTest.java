package br.com.reservas.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.AreaTestStorageConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * FD-1 (docs/04): `LocalSeedRunner` cria o cenario minimo do perfil `local`
 * (1 sindico, 2 unidades, 2 areas com foto) e e idempotente.
 *
 * <p>{@code app.seed.local.enabled=true} liga o bean so nesta classe (a
 * {@link AbstractIntegrationTest} base desliga o seed para o resto da suite,
 * ver o comentario la). Como este teste nao define
 * {@code app.seed.admin-email/password}, o `BootstrapRunner` nao cria ADMIN
 * na subida do contexto e o proprio `LocalSeedRunner` nao roda sozinho nesse
 * momento (guarda "ADMIN ausente"); o cenario e montado manualmente, dentro
 * da transacao do teste, chamando {@code run(null)} explicitamente — efeito
 * confinado a transacao e desfeito ao final, sem sujar os demais testes que
 * reusam este container Postgres.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "app.seed.local.enabled=true")
@Import(AreaTestStorageConfig.class)
@Transactional
class LocalSeedRunnerTest extends AbstractIntegrationTest {

    private static final String SEED_PASSWORD = "LocalDev123!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LocalSeedRunner runner;

    private UUID condominiumId;

    @BeforeEach
    void setUp() {
        condominiumId = jdbcTemplate.queryForObject(
            "insert into condominium (id, name) values (gen_random_uuid(), 'Condominio Seed Local') returning id",
            UUID.class);
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, "admin.seed." + UUID.randomUUID() + "@exemplo.test",
            passwordEncoder.encode("SenhaForteAdmin1"));
    }

    @Test
    @DisplayName("FD-1: seed local cria 1 sindico, 2 unidades e 2 areas com 1 foto cada")
    void createsMinimalScenario() throws Exception {
        runner.run(null);

        assertThat(countWhere("unit")).isEqualTo(2);
        assertThat(countWhere("common_area")).isEqualTo(2);
        assertThat(countWhere("user_account", "role = 'SYNDIC'")).isEqualTo(1);
        Long photoCount = jdbcTemplate.queryForObject(
            "select count(*) from area_photo p join common_area a on a.id = p.area_id where a.condominium_id = ?",
            Long.class, condominiumId);
        assertThat(photoCount).isEqualTo(2);
    }

    @Test
    @DisplayName("FD-1: rodar o seed local de novo nao duplica nada (idempotente)")
    void doesNotDuplicateOnSecondRun() throws Exception {
        runner.run(null);
        runner.run(null);

        assertThat(countWhere("unit")).isEqualTo(2);
        assertThat(countWhere("common_area")).isEqualTo(2);
        assertThat(countWhere("user_account", "role = 'SYNDIC'")).isEqualTo(1);
    }

    @Test
    @DisplayName("FD-1: login da unidade a-101 criada pelo seed local funciona com a senha de seed")
    void unitA101LoginWorks() throws Exception {
        runner.run(null);

        String token = ApiLogin.token(mockMvc, objectMapper, "a-101", SEED_PASSWORD);
        mockMvc.perform(get("/api/v1/me/unit")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk());
    }

    private long countWhere(String table) {
        return countWhere(table, "1=1");
    }

    private long countWhere(String table, String extraCondition) {
        Long count = jdbcTemplate.queryForObject(
            "select count(*) from " + table + " where condominium_id = ? and " + extraCondition,
            Long.class, condominiumId);
        return count == null ? 0 : count;
    }
}
