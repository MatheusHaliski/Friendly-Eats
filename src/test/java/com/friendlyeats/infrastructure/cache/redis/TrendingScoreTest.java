package com.friendlyeats.infrastructure.cache.redis;

import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.UserCategoryEnum;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Fórmula e chaves do ranking "em alta" (RF14), portadas da Orange Social. */
class TrendingScoreTest {

    @Test
    void newerPostWinsOverOlderWithSameEngagement() {
        assertThat(TrendingScoreCalculator.score(0, 10, 2, 1, 3, 0))
                .isGreaterThan(TrendingScoreCalculator.score(0, 10, 2, 1, 3, 24));
    }

    @Test
    void savesAndSharesWeighMoreThanLikesAndViewsAreLogarithmic() {
        assertThat(TrendingScoreCalculator.engagement(0, 0, 0, 1, 0))
                .isGreaterThan(TrendingScoreCalculator.engagement(0, 1, 0, 0, 0));
        assertThat(TrendingScoreCalculator.engagement(0, 0, 0, 0, 1))
                .isGreaterThan(TrendingScoreCalculator.engagement(0, 1, 0, 0, 0));
        double e1 = TrendingScoreCalculator.engagement(1_000, 0, 0, 0, 0);
        double e10 = TrendingScoreCalculator.engagement(10_000, 0, 0, 0, 0);
        assertThat(e10 / e1).isLessThan(2.0);
    }

    @Test
    void incrementUpdatesTheFeedAndTheAuthorTypeRankings() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ZSetOperations<String, String> zset = mock(ZSetOperations.class);
        when(redis.opsForZSet()).thenReturn(zset);
        when(zset.incrementScore(anyString(), anyString(), anyDouble())).thenReturn(42.0);

        double score = new TrendingScoreRepository(redis).increment(FeedType.POST, UserCategoryEnum.CHEF, "p1", 1.5);

        assertThat(score).isEqualTo(42.0);
        verify(zset).incrementScore("fe:trending:post", "p1", 1.5);
        verify(zset).incrementScore("fe:trending:post:chef", "p1", 1.5);
        verify(redis).expire("fe:trending:post", TrendingScoreRepository.KEY_TTL);
    }
}
