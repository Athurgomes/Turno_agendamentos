package br.com.reservas.auth.infra.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D-48: garante que o valor de exemplo centralizado em {@link ExampleSecrets}
 * e exatamente o mesmo usado em {@code application-local.yml} (dentro do
 * modulo backend; o {@code docker-compose.yml} e o {@code .env.example} na
 * raiz do repositorio sao conferidos manualmente, fora do build do backend,
 * que so monta {@code backend/} — ver resumo da tarefa).
 */
class ExampleSecretsConsistencyTest {

    @Test
    @DisplayName("D-48: application-local.yml usa o segredo JWT de exemplo centralizado")
    void localYamlUsesCentralizedJwtSecret() throws IOException {
        assertThat(read("src/main/resources/application-local.yml")).contains(ExampleSecrets.JWT_SECRET);
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(relativePath));
    }
}
