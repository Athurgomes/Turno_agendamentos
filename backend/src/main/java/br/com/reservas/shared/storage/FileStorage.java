package br.com.reservas.shared.storage;

import java.io.InputStream;
import java.net.URI;

/**
 * Armazenamento de arquivos S3-compativel (D-27): MinIO no local, Supabase
 * Storage na publicacao gratuita. Unica implementacao no MVP; a interface
 * existe para o resto do codigo nunca falar em bucket/credenciais/SDK.
 *
 * <p><b>Chaves:</b> o chamador gera uma chave unica antes de chamar
 * {@code put} (ex.: {@code areas/{areaId}/{uuid}.{ext}}, RN-17). Esta classe
 * nunca verifica unicidade nem gera a chave: chamar {@code put} duas vezes com
 * a mesma chave sobrescreve o objeto anterior.
 */
public interface FileStorage {

    void put(String key, byte[] content, String contentType);

    void put(String key, InputStream content, long contentLength, String contentType);

    /** URL de leitura pre-assinada, valida por 5 minutos (D-41), no prefixo publico configurado. */
    URI presignedGetUrl(String key);

    boolean exists(String key);
}
