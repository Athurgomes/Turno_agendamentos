package br.com.reservas.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.reservas.area.support.TestImages;
import br.com.reservas.reservation.support.ReservationFixtures;
import br.com.reservas.reservation.support.ReservationTestConfig;
import br.com.reservas.support.AbstractIntegrationTest;
import br.com.reservas.support.ApiLogin;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RNF-03/RN-24/D-07: prova, com HTTP real concorrente (não MockMvc, que serializa
 * as requisições), que a exclusion constraint do banco (não checagem só na
 * aplicação) garante no máximo 1 reserva ativa por horário/área mesmo sob
 * concorrência real — as demais viram 409 RESERVATION_OVERLAP, nunca 500.
 *
 * <p>Contexto próprio (não reaproveita o cache de {@code @SpringBootTest} das
 * outras classes de {@code reservation}) porque precisa de
 * {@code webEnvironment = RANDOM_PORT} (servidor HTTP de verdade) e, por isso,
 * roda sem {@code @Transactional}: os dados de setup precisam estar
 * efetivamente commitados para serem visíveis às conexões dos 50 threads que
 * batem no servidor. Cada execução usa um condomínio/área com UUIDs novos, então
 * não interfere com execuções anteriores nem com as demais classes de teste; o
 * {@code @AfterEach} desfaz manualmente os inserts (sem transação para reverter).
 *
 * <p>{@code @DirtiesContext(AFTER_CLASS)}: este é o único teste do módulo com
 * {@code webEnvironment = RANDOM_PORT}, então o contexto (e o pool Hikari de 10
 * conexões que ele abre) não é reaproveitado por nenhuma outra classe — differente
 * do padrão de {@link ReservationTestConfig}. Sem isso, o Spring mantém esse
 * contexto (e o pool) em cache até o fim da suíte inteira, somado a todos os
 * outros contextos distintos já cacheados; isso violou o {@code max_connections}
 * do Postgres do Testcontainers ("too many clients already") ao rodar a suíte
 * completa, quebrando testes não relacionados (ex.: SystemClockControllerTest).
 * Fechar o contexto assim que esta classe termina libera as conexões antes das
 * classes seguintes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(ReservationTestConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ConcurrentReservationFlowTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "SenhaForteConta1";
    private static final int CONCURRENT_REQUESTS = 50;
    private static final LocalDate BOOKING_DATE = LocalDate.of(2026, 11, 13);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Environment environment;

    private UUID condominiumId;

    /**
     * Sem {@code @Transactional} (ver javadoc da classe), os inserts deste teste
     * ficam commitados de verdade; outros testes da suíte que fazem
     * {@code SELECT count(*)} sem filtro por condomínio (ex.: MigrationV6Test)
     * dependem de a tabela `reservation` começar vazia. Desfazemos manualmente,
     * na ordem que respeita as FKs, tudo que este teste criou.
     */
    @AfterEach
    void tearDown() {
        if (condominiumId == null) {
            return;
        }
        jdbcTemplate.update("delete from refresh_token where user_id in "
            + "(select id from user_account where condominium_id = ?)", condominiumId);
        jdbcTemplate.update("delete from audit_log where condominium_id = ?", condominiumId);
        jdbcTemplate.update("delete from reservation_event where reservation_id in "
            + "(select id from reservation where condominium_id = ?)", condominiumId);
        jdbcTemplate.update("delete from reservation where condominium_id = ?", condominiumId);
        jdbcTemplate.update("delete from area_photo where area_id in "
            + "(select id from common_area where condominium_id = ?)", condominiumId);
        jdbcTemplate.update("delete from area_inspection where area_id in "
            + "(select id from common_area where condominium_id = ?)", condominiumId);
        jdbcTemplate.update("delete from area_opening_hours where area_id in "
            + "(select id from common_area where condominium_id = ?)", condominiumId);
        jdbcTemplate.update("delete from common_area where condominium_id = ?", condominiumId);
        jdbcTemplate.update("delete from resident where unit_id in "
            + "(select id from unit where condominium_id = ?)", condominiumId);
        jdbcTemplate.update("delete from user_account where condominium_id = ?", condominiumId);
        jdbcTemplate.update("delete from unit where condominium_id = ?", condominiumId);
        jdbcTemplate.update("delete from condominium_settings where condominium_id = ?", condominiumId);
        jdbcTemplate.update("delete from condominium where id = ?", condominiumId);
    }

    @Test
    @DisplayName("RNF-03/RN-24: 50 requisicoes simultaneas no mesmo horario da mesma area "
        + "resultam em exatamente 1 reserva criada e 49 RESERVATION_OVERLAP")
    void fiftyConcurrentRequestsForSameSlotYieldExactlyOneReservation() throws Exception {
        condominiumId = ReservationFixtures.insertCondominium(jdbcTemplate, "Condominio Concorrencia");
        // RN-22 fica fora de escopo deste teste: cada requisicao usa uma unidade
        // diferente, mas zera o limite tambem por seguranca/clareza de intencao.
        jdbcTemplate.update(
            "update condominium_settings set max_active_bookings_per_unit = 0 where condominium_id = ?",
            condominiumId);
        String adminToken = createAdmin(condominiumId);
        UUID areaId = createArea(adminToken);

        List<UnitRequest> requests = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            UUID unitId = ReservationFixtures.insertUnit(jdbcTemplate, condominiumId, "conc-" + i);
            UUID residentId = ReservationFixtures.insertResident(jdbcTemplate, unitId, "Morador " + i);
            String unitToken = createUnitAccount(condominiumId, unitId, "conc-" + i);
            requests.add(new UnitRequest(unitToken, reservationPayload(areaId, residentId)));
        }

        int port = Integer.parseInt(environment.getProperty("local.server.port"));
        URI endpoint = URI.create("http://127.0.0.1:" + port + "/api/v1/reservations");
        HttpClient client = HttpClient.newHttpClient();

        CountDownLatch ready = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        List<Future<HttpResponse<String>>> futures = new ArrayList<>();

        for (UnitRequest unitRequest : requests) {
            Callable<HttpResponse<String>> task = () -> {
                HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + unitRequest.token())
                    .POST(HttpRequest.BodyPublishers.ofString(unitRequest.body()))
                    .build();
                ready.countDown();
                start.await();
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            };
            futures.add(executor.submit(task));
        }

        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        // RNF-03/D-57: 20s de folga por futuro (o lote inteiro leva ~1-2s na maquina de
        // desenvolvimento). Antes do ReservationRepository#lockArea, 50 requisicoes
        // concorrentes na mesma area podiam formar um ciclo real de deadlock no indice
        // GiST da exclusion constraint (RN-24) entre 3+ transacoes (nao so espera par-a-par);
        // o Postgres so resolve um ciclo por vez, a cada deadlock_timeout (~1s, medido: um
        // "deadlock detected" por segundo), o que podia esgotar os 30s do
        // HikariPool.connectionTimeout para as ultimas requisicoes na fila (nao era o
        // cliente HTTP esperando, era o proprio backend sem conexao livre -> 500).
        List<HttpResponse<String>> responses = new CopyOnWriteArrayList<>();
        for (Future<HttpResponse<String>> future : futures) {
            responses.add(future.get(20, TimeUnit.SECONDS));
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();

        assertThat(responses).hasSize(CONCURRENT_REQUESTS);

        long created = responses.stream().filter(r -> r.statusCode() == 201).count();
        long conflicts = responses.stream().filter(r -> r.statusCode() == 409).count();
        List<HttpResponse<String>> unexpected = responses.stream()
            .filter(r -> r.statusCode() != 201 && r.statusCode() != 409)
            .toList();
        assertThat(unexpected)
            .withFailMessage("Respostas inesperadas (nao 201/409): %s", describe(unexpected))
            .isEmpty();

        for (HttpResponse<String> response : responses) {
            if (response.statusCode() == 409) {
                JsonNode body = objectMapper.readTree(response.body());
                assertThat(body.get("code").asText()).isEqualTo("RESERVATION_OVERLAP");
            }
        }

        assertThat(created).isEqualTo(1);
        assertThat(conflicts).isEqualTo(49);

        Integer activeInDb = jdbcTemplate.queryForObject(
            "select count(*) from reservation where area_id = ? and status in ('CONFIRMED', 'PENDING_PAYMENT') "
                + "and start_at = ? and end_at = ?",
            Integer.class, areaId, startTimestamp(), endTimestamp());
        assertThat(activeInDb).isEqualTo(1);
    }

    private java.sql.Timestamp startTimestamp() {
        return java.sql.Timestamp.from(
            BOOKING_DATE.atTime(14, 0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant());
    }

    private java.sql.Timestamp endTimestamp() {
        return java.sql.Timestamp.from(
            BOOKING_DATE.atTime(16, 0).atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant());
    }

    private record UnitRequest(String token, String body) {
    }

    private String reservationPayload(UUID areaId, UUID residentId) {
        return """
            { "areaId": "%s", "date": "%s", "startTime": "14:00", "endTime": "16:00", \
            "residentId": "%s", "guests": 2 }
            """.formatted(areaId, BOOKING_DATE, residentId);
    }

    private String describe(List<HttpResponse<String>> responses) {
        StringBuilder sb = new StringBuilder();
        for (HttpResponse<String> response : responses) {
            sb.append(response.statusCode()).append(": ").append(response.body()).append('\n');
        }
        return sb.toString();
    }

    private UUID createArea(String adminToken) throws Exception {
        String payload = """
            {
              "name": "Area Concorrencia %s",
              "category": "PARTY_ROOM",
              "description": "Descricao",
              "rules": "Regras",
              "conductGuidelines": "Conduta",
              "capacity": 200,
              "requiresPayment": false,
              "openingHours": [
                { "dayOfWeek": 1, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 2, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 3, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 4, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 5, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 6, "openTime": "00:00", "closeTime": "23:30" },
                { "dayOfWeek": 7, "openTime": "00:00", "closeTime": "23:30" }
              ]
            }
            """.formatted(UUID.randomUUID());

        var request = multipart("/api/v1/areas")
            .file(new MockMultipartFile("data", "data", "application/json", payload.getBytes()))
            .file(new MockMultipartFile("photos", "foto.png", "image/png", TestImages.png()))
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken);
        String response = mockMvc.perform(request)
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String createAdmin(UUID condominiumId) throws Exception {
        String email = "admin.concorrencia." + UUID.randomUUID() + "@exemplo.test";
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, email, display_name, password_hash) "
                + "values (?, 'ADMIN', ?, 'Admin', ?)",
            condominiumId, email, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, email, PASSWORD);
    }

    private String createUnitAccount(UUID condominiumId, UUID unitId, String username) throws Exception {
        jdbcTemplate.update(
            "insert into user_account (condominium_id, role, username, unit_id, password_hash) "
                + "values (?, 'UNIT', ?, ?, ?)",
            condominiumId, username, unitId, passwordEncoder.encode(PASSWORD));
        return ApiLogin.token(mockMvc, objectMapper, username, PASSWORD);
    }
}
