package br.com.reservas.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Classe base para testes de integracao (F0-5). Sobe um unico PostgreSQL 16
 * real via Testcontainers, compartilhado por todas as subclasses do processo
 * de teste ("singleton container pattern"): o container e criado e iniciado
 * uma unica vez no carregamento desta classe e nunca e parado explicitamente
 * — quem o remove ao final e o Ryuk (sidecar do Testcontainers), evitando
 * subir/derrubar um Postgres a cada classe de teste.
 *
 * {@code @ServiceConnection} injeta automaticamente as propriedades de
 * datasource do Spring Boot a partir do container, sem precisar declarar
 * {@code spring.datasource.*} manualmente nos testes (essas propriedades
 * de conexao continuam ligadas no {@code application.yml} principal, mas nao
 * chegam a ser usadas). O perfil {@code local} e ativado so para fornecer os
 * defaults de {@code APP_JWT_SECRET} (o principal exige a variavel, F1-2).
 *
 * <p>FD-1: {@code app.seed.local.enabled=false} desliga o {@code LocalSeedRunner}
 * neste contexto compartilhado (ele tambem roda com o perfil {@code local}, mas
 * os testes deste pacote nao esperam unidades/areas ficticias pre-existentes).
 * O proprio teste do seed sobrescreve essa propriedade de volta para {@code true}.
 */
@ActiveProfiles("local")
@TestPropertySource(properties = {
    "app.seed.local.enabled=false",
    // F5-2/RN-31: o job roda em background por conta própria; os testes de expiração
    // chamam ReservationService.expirePendingIfNeeded(...) direto com o Clock de teste,
    // então o job real (relógio do sistema) fica desligado para não interferir.
    "app.reservations.expiration-job-enabled=false",
    // D-39/RNF-03: a primeira execução real do CI (runner de 2 vCPUs) derrubou a
    // suíte com "FATAL: sorry, too many clients already". Causa: 47 classes
    // @SpringBootTest, cada combinação de perfis/propriedades cria um
    // ApplicationContext cacheado com seu próprio pool HikariCP (default 10
    // conexões cada), todos batendo neste único Postgres; num runner mais fraco a
    // eviction de contexto é mais lenta e a soma passa do teto do Postgres.
    // Limitar o pool aqui (a maioria dos testes usa 1 conexão por vez) resolve o
    // lado da aplicação; o ConcurrentReservationFlowTest, que precisa de mais
    // conexões simultâneas para as 50 requisições reais, sobrescreve este valor
    // na própria classe.
    "spring.datasource.hikari.maximum-pool-size=3",
    "spring.datasource.hikari.minimum-idle=1"
})
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
            // D-39/RNF-03: sobe o teto do Postgres de teste para acomodar com folga a
            // soma dos pools Hikari de todos os ApplicationContexts cacheados pela
            // suíte (ver comentário acima), mesmo num runner de CI mais lento.
            .withCommand("postgres", "-c", "max_connections=300");

    static {
        POSTGRES.start();
    }
}
