package br.com.reservas.shared.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import com.github.dockerjava.api.model.Volume;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * D-27/D-41/D-46: Versity S3 Gateway real via Testcontainers (GenericContainer;
 * nao ha modulo Testcontainers proprio para esse servidor). {@code
 * APP_STORAGE_PUBLIC_URL} nos outros ambientes vira `/storage` (relativo, atras
 * do proxy do nginx); aqui usamos o proprio endpoint do container para o teste
 * que baixa o arquivo direto pela URL pre-assinada, sem precisar de um proxy.
 */
@Testcontainers
class S3FileStorageIntegrationTest {

    private static final String BUCKET = "reservas-teste";
    private static final String ACCESS_KEY = "reservas-teste-access";
    private static final String SECRET_KEY = "reservas-teste-secret";

    private static final GenericContainer<?> S3 = new GenericContainer<>(
        DockerImageName.parse("versity/versitygw:v1.8.0"))
        .withExposedPorts(7070)
        .withEnv("VGW_BACKEND", "posix")
        .withEnv("VGW_BACKEND_ARG", "/data")
        .withEnv("ROOT_ACCESS_KEY_ID", ACCESS_KEY)
        .withEnv("ROOT_SECRET_ACCESS_KEY", SECRET_KEY)
        // A imagem nao vem com "/data" pre-criado; declarar o volume anonimo faz o
        // Docker provisiona-lo antes do entrypoint rodar (sem depender de um path
        // de host, que nao existiria no cenario docker-outside-of-docker do CI).
        .withCreateContainerCmdModifier(cmd -> cmd.withVolumes(new Volume("/data")))
        .waitingFor(Wait.forLogMessage(".*Admin/S3 service listening.*\\n", 1));

    private static String endpoint;
    private static S3FileStorage fileStorage;

    @BeforeAll
    static void startContainer() {
        S3.start();
        endpoint = "http://" + S3.getHost() + ":" + S3.getMappedPort(7070);

        // D-46: o bucket nao e pre-criado por um cliente admin (a imagem nao tem
        // `mc`); e o proprio backend quem garante o bucket na subida.
        StorageProperties properties = new StorageProperties(
            endpoint, endpoint, BUCKET, ACCESS_KEY, SECRET_KEY, "us-east-1", true);
        fileStorage = new S3FileStorage(properties);
        fileStorage.ensureBucketExists();
    }

    @AfterAll
    static void stopContainer() {
        S3.stop();
    }

    @Test
    @DisplayName("D-27/D-41: put + presignedGetUrl gera uma URL que devolve o conteudo gravado")
    void putAndDownloadThroughPresignedUrl() throws Exception {
        String key = "areas/teste/" + java.util.UUID.randomUUID() + ".txt";
        byte[] content = "conteudo de teste".getBytes(StandardCharsets.UTF_8);

        fileStorage.put(key, content, "text/plain");
        URI presignedUrl = fileStorage.presignedGetUrl(key);

        HttpResponse<String> response = HttpClient.newHttpClient()
            .send(HttpRequest.newBuilder(presignedUrl).GET().build(), HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("conteudo de teste");
    }

    @Test
    @DisplayName("D-27: exists() diferencia chave gravada de chave inexistente")
    void existsReflectsWhetherKeyWasWritten() {
        String key = "areas/teste/" + java.util.UUID.randomUUID() + ".txt";
        assertThat(fileStorage.exists(key)).isFalse();

        fileStorage.put(key, new ByteArrayInputStream("x".getBytes(StandardCharsets.UTF_8)), 1, "text/plain");

        assertThat(fileStorage.exists(key)).isTrue();
    }

    @Test
    @DisplayName("D-46: ensureBucketExists() cria o bucket quando ele ainda nao existe")
    void ensureBucketExistsCreatesMissingBucket() {
        StorageProperties otherBucket = new StorageProperties(
            endpoint, endpoint, "reservas-teste-novo", ACCESS_KEY, SECRET_KEY, "us-east-1", true);
        S3FileStorage storageWithNewBucket = new S3FileStorage(otherBucket);

        storageWithNewBucket.ensureBucketExists();
        storageWithNewBucket.put("marker.txt", "ok".getBytes(StandardCharsets.UTF_8), "text/plain");

        assertThat(storageWithNewBucket.exists("marker.txt")).isTrue();
    }

    @Test
    @DisplayName("D-41: presignedGetUrl troca o host interno pelo prefixo publico /storage")
    void presignedGetUrlSwapsPrefixForPublicUrl() {
        StorageProperties behindProxy = new StorageProperties(
            endpoint, "/storage", BUCKET, ACCESS_KEY, SECRET_KEY, "us-east-1", true);
        S3FileStorage storageBehindProxy = new S3FileStorage(behindProxy);

        String key = "areas/teste/" + java.util.UUID.randomUUID() + ".txt";
        storageBehindProxy.put(key, "conteudo".getBytes(StandardCharsets.UTF_8), "text/plain");

        URI presignedUrl = storageBehindProxy.presignedGetUrl(key);

        assertThat(presignedUrl.toString()).startsWith("/storage/");
        assertThat(presignedUrl.getQuery()).contains("X-Amz-Signature");
    }
}
