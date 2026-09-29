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
    "app.reservations.expiration-job-enabled=false"
})
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
    }
}
