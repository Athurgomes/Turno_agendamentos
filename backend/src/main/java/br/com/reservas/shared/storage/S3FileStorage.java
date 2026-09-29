package br.com.reservas.shared.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/** Única implementação de {@link FileStorage}: Versity S3 Gateway local ou Supabase Storage (D-27, D-46). */
@Component
public class S3FileStorage implements FileStorage {

    private static final Logger log = LoggerFactory.getLogger(S3FileStorage.class);
    private static final Duration PRESIGN_DURATION = Duration.ofMinutes(5);
    private static final int ENSURE_BUCKET_ATTEMPTS = 5;
    private static final Duration ENSURE_BUCKET_RETRY_DELAY = Duration.ofMillis(500);

    private final S3Client client;
    private final S3Presigner presigner;
    private final StorageProperties properties;

    public S3FileStorage(StorageProperties properties) {
        this.properties = properties;
        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        var pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.client = S3Client.builder()
            .endpointOverride(URI.create(properties.endpoint()))
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials)
            .serviceConfiguration(pathStyle)
            .build();
        this.presigner = S3Presigner.builder()
            .endpointOverride(URI.create(properties.endpoint()))
            .region(Region.of(properties.region()))
            .credentialsProvider(credentials)
            .serviceConfiguration(pathStyle)
            .build();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        client.putObject(putRequest(key, contentType), RequestBody.fromBytes(content));
    }

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        client.putObject(putRequest(key, contentType), RequestBody.fromInputStream(content, contentLength));
    }

    @Override
    public URI presignedGetUrl(String key) {
        GetObjectRequest getRequest = GetObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_DURATION)
            .getObjectRequest(getRequest)
            .build();

        // D-41: troca o host interno (endpoint do MinIO/Supabase) pelo prefixo publico,
        // mantendo path e assinatura (query string) intactos.
        URI internal = URI.create(presigner.presignGetObject(presignRequest).url().toString());
        String query = internal.getRawQuery();
        return URI.create(properties.publicUrl() + internal.getRawPath() + (query != null ? "?" + query : ""));
    }

    /**
     * D-46: cria o bucket configurado se ele ainda não existir, com retry curto
     * (o S3 pode subir alguns segundos depois do backend no compose). Nunca
     * lança: se esgotar as tentativas, só loga o erro e a aplicação continua
     * (uploads falharão até o S3/bucket ficarem disponíveis).
     */
    public void ensureBucketExists() {
        if (!properties.createBucket()) {
            return;
        }
        for (int attempt = 1; attempt <= ENSURE_BUCKET_ATTEMPTS; attempt++) {
            try {
                if (!bucketExists()) {
                    client.createBucket(b -> b.bucket(properties.bucket()));
                    log.info("Bucket '{}' criado no S3.", properties.bucket());
                } else {
                    log.info("Bucket '{}' já existe no S3.", properties.bucket());
                }
                return;
            } catch (SdkException e) {
                if (attempt == ENSURE_BUCKET_ATTEMPTS) {
                    log.error("Não foi possível garantir o bucket '{}' após {} tentativas: {}. "
                        + "A aplicação continua; uploads falharão até o S3 ficar disponível.",
                        properties.bucket(), ENSURE_BUCKET_ATTEMPTS, e.getMessage());
                    return;
                }
                log.warn("Tentativa {}/{} de garantir o bucket '{}' falhou: {}",
                    attempt, ENSURE_BUCKET_ATTEMPTS, properties.bucket(), e.getMessage());
                sleep(ENSURE_BUCKET_RETRY_DELAY);
            }
        }
    }

    private boolean bucketExists() {
        try {
            client.headBucket(b -> b.bucket(properties.bucket()));
            return true;
        } catch (NoSuchBucketException e) {
            return false;
        }
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean exists(String key) {
        try {
            client.headObject(b -> b.bucket(properties.bucket()).key(key));
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        }
    }

    private PutObjectRequest putRequest(String key, String contentType) {
        return PutObjectRequest.builder()
            .bucket(properties.bucket())
            .key(key)
            .contentType(contentType)
            .build();
    }
}
