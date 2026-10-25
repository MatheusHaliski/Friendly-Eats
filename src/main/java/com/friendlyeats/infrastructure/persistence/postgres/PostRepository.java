package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Agregado Post (RF9): o post, o perfil do autor (RF15) e as escritas atômicas das interações (RF12). Reações e
 * compartilhamentos não têm entidade própria: são linhas de associação gravadas com {@code ON CONFLICT DO NOTHING}, e
 * o contador só muda quando a linha realmente entrou ou saiu — por isso as operações são idempotentes. O feed com
 * filtros fica em {@link PostFeedQueries}.
 */
public interface PostRepository extends JpaRepository<Post, UUID> {

    // ------------------------------------------------------------ perfil do autor (RF15)

    @Query(value = """
            SELECT * FROM post
            WHERE author_id = :authorId AND status IN ('PUBLISHED','ARCHIVED')
              AND (:adult OR age_rating = 'LIVRE')
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Post> findProfileFeed(Long authorId, boolean adult, int limit);

    /** Página seguinte por cursor (created_at, id): estável mesmo com posts novos chegando. */
    @Query(value = """
            SELECT * FROM post
            WHERE author_id = :authorId AND status IN ('PUBLISHED','ARCHIVED')
              AND (:adult OR age_rating = 'LIVRE')
              AND (created_at, id) < (:beforeCreatedAt, :beforeId)
            ORDER BY created_at DESC, id DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Post> findProfileFeedBefore(Long authorId, boolean adult, Instant beforeCreatedAt, UUID beforeId, int limit);

    long countByAuthorIdAndStatusIn(Long authorId, Collection<PostStatus> statuses);

    // ------------------------------------------------------------ reações (LIKE / SAVE)

    @Modifying
    @Query(value = """
            INSERT INTO post_reaction (post_id, user_id, kind) VALUES (:postId, :userId, :kind)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertReaction(UUID postId, Long userId, String kind);

    @Modifying
    @Query(value = "DELETE FROM post_reaction WHERE post_id = :postId AND user_id = :userId AND kind = :kind",
            nativeQuery = true)
    int deleteReaction(UUID postId, Long userId, String kind);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM post_reaction WHERE post_id = :postId AND user_id = :userId AND kind = :kind)
            """, nativeQuery = true)
    boolean existsReaction(UUID postId, Long userId, String kind);

    /** Quais destes posts o usuário já curtiu ou salvou (estado dos botões nos cards do feed). */
    @Query(value = """
            SELECT post_id FROM post_reaction
            WHERE user_id = :userId AND kind = :kind AND post_id IN (:postIds)
            """, nativeQuery = true)
    List<UUID> findReactedPostIds(Long userId, String kind, Collection<UUID> postIds);

    @Query(value = "SELECT count(*) FROM post_reaction WHERE post_id = :postId AND kind = 'SAVE'", nativeQuery = true)
    long countSaves(UUID postId);

    // ------------------------------------------------------------ compartilhamentos

    @Modifying
    @Query(value = """
            INSERT INTO post_share (post_id, user_id, channel) VALUES (:postId, :userId, :channel)
            ON CONFLICT ON CONSTRAINT uk_post_share_user_channel DO NOTHING
            """, nativeQuery = true)
    int insertShare(UUID postId, Long userId, String channel);

    // ------------------------------------------------------------ contadores

    @Modifying
    @Query(value = "UPDATE post SET like_count = like_count + :delta WHERE id = :postId", nativeQuery = true)
    int addToLikeCount(UUID postId, int delta);

    @Modifying
    @Query(value = "UPDATE post SET comment_count = comment_count + :delta WHERE id = :postId", nativeQuery = true)
    int addToCommentCount(UUID postId, int delta);

    @Modifying
    @Query(value = "UPDATE post SET share_count = share_count + :delta WHERE id = :postId", nativeQuery = true)
    int addToShareCount(UUID postId, int delta);

    /** Lido depois do UPDATE, na mesma transação: a linha está travada e o valor é o atual. */
    @Query(value = """
            SELECT like_count AS likes, comment_count AS comments, share_count AS shares
            FROM post WHERE id = :postId
            """, nativeQuery = true)
    PostCounters findCounters(UUID postId);

    interface PostCounters {
        long getLikes();

        long getComments();

        long getShares();
    }

    // ------------------------------------------------------------ em alta (RF14)

    /** Publicados a partir de {@code since}, com os contadores e os salvamentos, para recalcular o ranking. */
    @Query(value = """
            SELECT p.id AS id, p.feed_type AS feed, p.author_user_type AS authorType, p.created_at AS createdAt,
                   p.like_count AS likes, p.comment_count AS comments, p.share_count AS shares,
                   (SELECT count(*) FROM post_reaction r WHERE r.post_id = p.id AND r.kind = 'SAVE') AS saves
            FROM post p
            WHERE p.status = 'PUBLISHED' AND p.created_at >= :since
              AND (p.expires_at IS NULL OR p.expires_at > :now)
            """, nativeQuery = true)
    List<TrendingRow> findTrendingCandidates(Instant since, Instant now);

    interface TrendingRow {
        UUID getId();

        String getFeed();

        String getAuthorType();

        Instant getCreatedAt();

        long getLikes();

        long getComments();

        long getShares();

        long getSaves();
    }

    // ------------------------------------------------------------ eliminação da conta (RF5)

    @Query(value = "SELECT id FROM post WHERE author_id = :userId AND status <> 'REMOVED'", nativeQuery = true)
    List<UUID> findLiveIdsByAuthor(Long userId);

    /** Chaves S3 das fotos do autor: saem do bucket depois do commit da eliminação. */
    @Query(value = """
            SELECT media_object_key FROM post
            WHERE author_id = :userId AND media_object_key IS NOT NULL
            """, nativeQuery = true)
    List<String> findMediaKeysByAuthor(Long userId);

    @Modifying
    @Query(value = """
            UPDATE post p SET like_count = p.like_count - 1
            FROM post_reaction r
            WHERE r.post_id = p.id AND r.user_id = :userId AND r.kind = 'LIKE'
            """, nativeQuery = true)
    int discountLikesOf(Long userId);

    @Modifying
    @Query(value = "DELETE FROM post_reaction WHERE user_id = :userId", nativeQuery = true)
    int deleteReactionsOf(Long userId);

    @Modifying
    @Query(value = "UPDATE post_share SET user_id = NULL WHERE user_id = :userId", nativeQuery = true)
    int anonymizeSharesOf(Long userId);

    @Modifying
    @Query(value = """
            UPDATE post SET status = 'REMOVED', title = 'Conteúdo removido', body = NULL, media_object_key = NULL,
                            alt_text = NULL, recipe = NULL, food_alert = NULL, city = NULL, background = NULL,
                            price_cents = NULL, price_from_cents = NULL, price_to_cents = NULL,
                            discount_percent = NULL, removed_at = now(), updated_at = now(), version = version + 1
            WHERE author_id = :userId AND status <> 'REMOVED'
            """, nativeQuery = true)
    int removeAllPostsOf(Long userId);
}
