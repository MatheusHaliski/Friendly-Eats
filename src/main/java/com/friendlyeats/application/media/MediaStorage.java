package com.friendlyeats.application.media;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Bucket de mídia (API S3: AWS S3 em produção, Garage em desenvolvimento). O banco guarda só a chave do objeto;
 * o cliente envia o arquivo direto ao bucket por uma URL pré-assinada e a API nunca recebe os bytes do upload.
 */
public interface MediaStorage {

    /** URL de {@code PUT} válida por {@code ttl}, presa ao tipo e ao tamanho exatos declarados. */
    PresignedUpload presignPut(String objectKey, String contentType, long sizeBytes, Duration ttl);

    /** Metadados do objeto ou vazio se ele não existe. */
    Optional<StoredObject> head(String objectKey);

    /** Lê o objeto inteiro; quem chama já conferiu o tamanho no {@link #head}. */
    byte[] read(String objectKey);

    void write(String objectKey, byte[] bytes, String contentType);

    /**
     * Objeto privado (exportação de dados, RF18): sem cache público e baixado com {@code downloadName}. Só se lê
     * por {@link #presignGet}.
     */
    void writePrivate(String objectKey, byte[] bytes, String contentType, String downloadName);

    /** URL de {@code GET} válida por {@code ttl} (exportação de dados, RF18). */
    String presignGet(String objectKey, Duration ttl);

    /** Apagar é idempotente: objeto que não existe não é erro. */
    void delete(String objectKey);

    record PresignedUpload(String url, String method, Map<String, String> headers, Instant expiresAt) {
    }

    record StoredObject(String contentType, long sizeBytes) {
    }
}
