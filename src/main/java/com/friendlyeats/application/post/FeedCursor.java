package com.friendlyeats.application.post;

import com.friendlyeats.domain.model.Post;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * Cursor opaco do feed (RF14, RF15): "micros-desde-epoch:uuid" do último post da página, em Base64 URL-safe. Estável
 * com posts novos chegando, porque a próxima página continua de onde a anterior parou ({@code (created_at, id) <}).
 */
public record FeedCursor(Instant createdAt, UUID id) {

    public static String encode(Post last) {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, last.getCreatedAt());
        String raw = micros + ":" + last.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Cursor que não veio de {@link #encode} dá {@code 400}. */
    public static FeedCursor decode(String cursor) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            int sep = raw.indexOf(':');
            long micros = Long.parseLong(raw.substring(0, sep));
            return new FeedCursor(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), UUID.fromString(raw.substring(sep + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cursor de paginação inválido.", e);
        }
    }
}
