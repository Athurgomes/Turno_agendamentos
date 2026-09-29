package br.com.reservas.area.support;

import br.com.reservas.shared.storage.FileStorage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fake em memoria de {@link FileStorage} para os testes de `area` (@SpringBootTest):
 * o backend-verify (F0-5) nao sobe um S3 real por padrao, e as regras de
 * `area` (magic bytes, chave nunca reutilizada, permissao por perfil) nao
 * dependem da mecanica real do S3 — essa ja tem cobertura dedicada em
 * {@code S3FileStorageIntegrationTest} (GenericContainer versitygw). A URL
 * "pre-assinada" aqui e so `memory://{key}`, o suficiente para os testes
 * afirmarem que ela muda a cada upload (RN-17) sem builder assinatura real.
 */
public class InMemoryFileStorage implements FileStorage {

    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

    @Override
    public void put(String key, byte[] content, String contentType) {
        objects.put(key, content);
    }

    @Override
    public void put(String key, InputStream content, long contentLength, String contentType) {
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            content.transferTo(buffer);
            objects.put(key, buffer.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URI presignedGetUrl(String key) {
        return URI.create("memory://" + key);
    }

    @Override
    public boolean exists(String key) {
        return objects.containsKey(key);
    }

    public byte[] get(String key) {
        return objects.get(key);
    }

    public int size() {
        return objects.size();
    }
}
