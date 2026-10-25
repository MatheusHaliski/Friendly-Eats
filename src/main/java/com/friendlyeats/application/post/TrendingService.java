package com.friendlyeats.application.post;

import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.cache.redis.TrendingScoreCalculator;
import com.friendlyeats.infrastructure.cache.redis.TrendingScoreCalculator.InteractionType;
import com.friendlyeats.infrastructure.cache.redis.TrendingScoreRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Ranking "em alta" (RF14) no Valkey. Cada escrita vai ao ZSET só depois do commit do PostgreSQL (uma interação que
 * voltou atrás não pontua) e em melhor esforço: o Valkey fora do ar vira aviso no log, e o recálculo periódico
 * ({@link #recalculate}) reconstrói o ranking a partir dos contadores do banco, aplicando o decaimento por idade.
 */
@Service
public class TrendingService {

    private static final Logger log = LoggerFactory.getLogger(TrendingService.class);
    /** Só posts publicados nas últimas 72 h disputam o ranking. */
    static final Duration WINDOW = Duration.ofHours(72);

    private final TrendingScoreRepository ranking;
    private final PostRepository posts;
    private final Clock clock;

    public TrendingService(TrendingScoreRepository ranking, PostRepository posts, Clock clock) {
        this.ranking = ranking;
        this.posts = posts;
        this.clock = clock;
    }

    /** Post publicado ou restaurado entra no ranking com score 0 (ZINCRBY 0 cria o membro sem mexer no valor). */
    public void published(Post post) {
        afterCommit(() -> ranking.increment(post.getFeedType(), post.getAuthorUserType(), post.getId().toString(), 0));
    }

    /** Interação que entrou ({@code +1}) ou saiu ({@code -1}) de verdade. */
    public void interaction(Post post, InteractionType type, int sign) {
        double delta = sign * TrendingScoreCalculator.incrementFor(type);
        afterCommit(() -> ranking.increment(post.getFeedType(), post.getAuthorUserType(), post.getId().toString(), delta));
    }

    /** Arquivado, apagado ou da conta eliminada: sai de todos os rankings. */
    public void withdraw(Collection<UUID> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        List<String> ids = postIds.stream().map(UUID::toString).toList();
        afterCommit(() -> ids.forEach(id -> {
            ranking.remove(FeedType.POST, id);
            ranking.remove(FeedType.ALERT, id);
        }));
    }

    public List<String> top(FeedType feed, UserCategoryEnum category, int limit) {
        return category == null ? ranking.topPostIds(feed, limit) : ranking.topPostIds(feed, category, limit);
    }

    /**
     * Recalcula todos os scores com o decaimento por idade (a fórmula completa), a partir dos contadores do banco.
     * Os encerrados e os mais velhos que a janela saem do ranking. Devolve quantos posts ficaram.
     */
    @Transactional(readOnly = true)
    public int recalculate() {
        Instant now = clock.instant();
        List<PostRepository.TrendingRow> rows = posts.findTrendingCandidates(now.minus(WINDOW), now);
        for (FeedType feed : List.of(FeedType.POST, FeedType.ALERT)) {
            ranking.clear(feed);
        }
        for (PostRepository.TrendingRow r : rows) {
            double hours = Math.max(0, Duration.between(r.getCreatedAt(), now).toMinutes() / 60.0);
            double score = TrendingScoreCalculator.score(0, r.getLikes(), r.getComments(), r.getShares(), r.getSaves(),
                    hours);
            ranking.setScore(FeedType.valueOf(r.getFeed()), UserCategoryEnum.valueOf(r.getAuthorType()),
                    r.getId().toString(), score);
        }
        return rows.size();
    }

    private static void afterCommit(Runnable action) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("Ranking em alta não atualizado agora (o recálculo corrige): {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
