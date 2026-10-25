package com.friendlyeats.infrastructure.cache.redis;

import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.UserCategoryEnum;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Ranking "em alta" em tempo real com ZSET no Valkey (protocolo Redis), RF14.
 *
 * <ul>
 *   <li>{@code fe:trending:{feed}}: ranking do feed ({@code post} ou {@code alert});</li>
 *   <li>{@code fe:trending:{feed}:{tipo de conta}}: o mesmo ranking filtrado pela cor do autor.</li>
 * </ul>
 * Membro = id do post, score = valor da {@link TrendingScoreCalculator}. O Valkey é só um índice: a visibilidade de
 * cada post (status, prazo, 18+, perfil privado) é conferida no PostgreSQL na leitura.
 */
@Repository
public class TrendingScoreRepository {

    private static final String PREFIX = "fe:trending:";
    static final Duration KEY_TTL = Duration.ofHours(72);

    private final StringRedisTemplate redis;

    public TrendingScoreRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String key(FeedType feed) {
        return PREFIX + feed.name().toLowerCase(Locale.ROOT);
    }

    static String key(FeedType feed, UserCategoryEnum category) {
        return key(feed) + ":" + category.name().toLowerCase(Locale.ROOT);
    }

    /** Define o score absoluto (ex.: depois do recálculo periódico). */
    public void setScore(FeedType feed, UserCategoryEnum authorCategory, String postId, double score) {
        zset().add(key(feed), postId, score);
        zset().add(key(feed, authorCategory), postId, score);
        touch(feed, authorCategory);
    }

    /** Soma o peso de uma interação (ZINCRBY) e devolve o novo score do feed. */
    public double increment(FeedType feed, UserCategoryEnum authorCategory, String postId, double delta) {
        Double updated = zset().incrementScore(key(feed), postId, delta);
        zset().incrementScore(key(feed, authorCategory), postId, delta);
        touch(feed, authorCategory);
        return updated == null ? 0.0 : updated;
    }

    public Optional<Double> getScore(FeedType feed, String postId) {
        return Optional.ofNullable(zset().score(key(feed), postId));
    }

    /** Posição (0 = topo) no ranking do feed. */
    public Optional<Long> getRank(FeedType feed, String postId) {
        return Optional.ofNullable(zset().reverseRank(key(feed), postId));
    }

    public List<String> topPostIds(FeedType feed, int limit) {
        return toList(zset().reverseRange(key(feed), 0, limit - 1L));
    }

    public List<String> topPostIds(FeedType feed, UserCategoryEnum category, int limit) {
        return toList(zset().reverseRange(key(feed, category), 0, limit - 1L));
    }

    /** Tira o post de todos os rankings do feed (arquivado, apagado, encerrado ou conta eliminada). */
    public void remove(FeedType feed, String postId) {
        zset().remove(key(feed), postId);
        for (UserCategoryEnum category : UserCategoryEnum.values()) {
            zset().remove(key(feed, category), postId);
        }
    }

    /** Esvazia os rankings de um feed antes de regravar o recálculo. */
    public void clear(FeedType feed) {
        redis.delete(key(feed));
        for (UserCategoryEnum category : UserCategoryEnum.values()) {
            redis.delete(key(feed, category));
        }
    }

    private void touch(FeedType feed, UserCategoryEnum category) {
        redis.expire(key(feed), KEY_TTL);
        redis.expire(key(feed, category), KEY_TTL);
    }

    private ZSetOperations<String, String> zset() {
        return redis.opsForZSet();
    }

    private static List<String> toList(Set<String> ids) {
        return ids == null ? List.of() : ids.stream().filter(Objects::nonNull).toList();
    }
}
