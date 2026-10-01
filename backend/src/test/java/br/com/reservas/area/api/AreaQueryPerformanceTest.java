package br.com.reservas.area.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.AreaTestStorageConfig;
import br.com.reservas.area.support.TestAreaReservationsGateway;
import br.com.reservas.area.support.TestImages;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * RNF-04: `GET /areas` e `GET /areas/{id}` nao podem crescer em numero de
 * consultas SQL conforme o numero de areas/fotos (N+1). Mede via Hibernate
 * {@link Statistics} (habilitada em runtime, sem propriedade de boot propria
 * — evita criar mais uma combinacao de contexto de teste cacheado, D-39) em
 * vez de tempo de resposta, que e ruidoso demais para um teste determinista.
 *
 * <p>Mesmas anotacoes de {@code AreaControllerTest} (mesmo contexto
 * cacheado). Fotos de vitrine sao inseridas via SQL direto com contas
 * {@code uploaded_by} nunca carregadas por JPA nesta sessao: assim o cache de
 * 1o nivel do Hibernate (a classe e {@code @Transactional}, como o resto da
 * suite) nao mascara o N+1 de autor da foto — cada id so pode vir do banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({AreaTestStorageConfig.class, TestAreaReservationsGateway.Config.class})
@Transactional
class AreaQueryPerformanceTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteAdmin1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UUID condominiumId;
    private String adminToken;
    private Statistics statistics;

    @BeforeEach
    void setUp() throws Exception {
        condominiumId = UUID.randomUUID();
        jdbcTemplate.update("insert into condominium (id, name) values (?, ?)", condominiumId, "Condominio Perf");
        String email = "admin." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin Teste', ?)",
            condominiumId, email, passwordEncoder.encode(PASSWORD));
        adminToken = ApiLogin.token(mockMvc, objectMapper, email, PASSWORD);

        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    @Test
    @DisplayName("RNF-04: GET /areas faz o mesmo numero de consultas com 2 ou 6 areas (sem N+1 de foto de capa)")
    void catalogQueryCountIsConstantRegardlessOfAreaCount() throws Exception {
        createArea();
        createArea();
        statistics.clear();
        mockMvc.perform(get("/api/v1/areas").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());
        long queriesWithTwoAreas = statistics.getPrepareStatementCount();

        createArea();
        createArea();
        createArea();
        createArea();
        statistics.clear();
        mockMvc.perform(get("/api/v1/areas").header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());
        long queriesWithSixAreas = statistics.getPrepareStatementCount();

        assertThat(queriesWithTwoAreas).isGreaterThan(0);
        assertThat(queriesWithSixAreas).isEqualTo(queriesWithTwoAreas);
    }

    @Test
    @DisplayName("RNF-04: GET /areas/{id} faz o mesmo numero de consultas com 1 ou 3 fotos de vitrine")
    void detailQueryCountIsConstantRegardlessOfShowcasePhotoCount() throws Exception {
        UUID areaWithOnePhoto = createArea();
        unfeatureCreationPhoto(areaWithOnePhoto);
        insertShowcasePhoto(areaWithOnePhoto, insertRawUploaderAccount());
        statistics.clear();
        mockMvc.perform(get("/api/v1/areas/" + areaWithOnePhoto)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());
        long queriesWithOnePhoto = statistics.getPrepareStatementCount();

        // 3 fotos de 3 uploaders diferentes, todos inseridos via SQL cru: nenhum id passou
        // pelo Hibernate antes, entao o cache de 1a nivel da sessao de teste nao ajuda —
        // o numero de consultas so fica igual entre os dois casos se o codigo de producao
        // realmente buscar os autores em lote (RNF-04).
        UUID areaWithThreePhotos = createArea();
        unfeatureCreationPhoto(areaWithThreePhotos);
        insertShowcasePhoto(areaWithThreePhotos, insertRawUploaderAccount());
        insertShowcasePhoto(areaWithThreePhotos, insertRawUploaderAccount());
        insertShowcasePhoto(areaWithThreePhotos, insertRawUploaderAccount());
        statistics.clear();
        mockMvc.perform(get("/api/v1/areas/" + areaWithThreePhotos)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isOk());
        long queriesWithThreePhotos = statistics.getPrepareStatementCount();

        assertThat(queriesWithOnePhoto).isGreaterThan(0);
        assertThat(queriesWithThreePhotos).isEqualTo(queriesWithOnePhoto);
    }

    /** Conta nunca tocada por JPA nesta sessao (insert via JDBC puro): garante consulta real. */
    private UUID insertRawUploaderAccount() {
        UUID accountId = UUID.randomUUID();
        String email = "uploader." + accountId + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (id, condominium_id, role, email, display_name, password_hash) "
                + "values (?, ?, 'SYNDIC', ?, 'Sindico Teste', ?)",
            accountId, condominiumId, email, passwordEncoder.encode(PASSWORD));
        return accountId;
    }

    /** Tira da vitrine a foto que nasce com a area (RN-11), para sobrar so a(s) inserida(s) no teste. */
    private void unfeatureCreationPhoto(UUID areaId) {
        jdbcTemplate.update("update area_photo set featured = false where area_id = ?", areaId);
    }

    private void insertShowcasePhoto(UUID areaId, UUID uploaderId) {
        jdbcTemplate.update(
            "insert into area_photo (area_id, storage_key, content_type, featured, archived, taken_at, "
                + "uploaded_by) values (?, ?, 'image/png', true, false, ?, ?)",
            areaId, "areas/" + areaId + "/" + UUID.randomUUID() + ".png", LocalDate.now(), uploaderId);
    }

    private UUID createArea() throws Exception {
        String dataJson = """
            {
              "name": "Salao %s",
              "category": "PARTY_ROOM",
              "description": "Descricao do salao",
              "rules": "Regras do salao",
              "conductGuidelines": "Sugestoes de conduta",
              "capacity": 50,
              "requiresPayment": false,
              "openingHours": [ { "dayOfWeek": 6, "openTime": "10:00", "closeTime": "22:00" } ]
            }
            """.formatted(UUID.randomUUID());
        var request = multipart("/api/v1/areas")
            .file(new MockMultipartFile("data", "data", "application/json", dataJson.getBytes()))
            .file(new MockMultipartFile("photos", "foto.png", "image/png", TestImages.png()));
        String response = mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }
}
