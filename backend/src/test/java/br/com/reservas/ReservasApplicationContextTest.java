package br.com.reservas;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.reservas.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * RNF-09 (portabilidade: sobe com Docker, sem Java/Node instalados) e
 * RNF-11 (observabilidade: Actuator health) — F0-5.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReservasApplicationContextTest extends AbstractIntegrationTest {

    @Autowired
    private Environment environment;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    @Test
    @DisplayName("RNF-09/RNF-11: contexto sobe com PostgreSQL real e /actuator/health responde UP")
    void contextLoadsAndHealthIsUp() {
        String port = environment.getProperty("local.server.port");
        ResponseEntity<String> response =
            restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"status\":\"UP\"");
    }
}
