package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.CommentStatus;
import com.friendlyeats.domain.model.PostComment;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

/** Caixa de mensagens do post (RF12) e a limpeza dos comentários na eliminação da conta (RF5). */
public interface PostCommentRepository extends JpaRepository<PostComment, Long> {

    /** Mais antigos primeiro, paginação por id crescente; sem os retirados pela moderação (RF13). */
    List<PostComment> findByPostIdAndStatusAndModerationStateIsNullAndIdGreaterThanOrderByIdAsc(
            UUID postId, CommentStatus status, long afterId, Limit limit);

    /**
     * Apaga o comentário só se ele ainda estiver visível. Duas exclusões ao mesmo tempo (quem escreveu e o dono do
     * post): a segunda espera a trava da linha, relê o WHERE e volta 0, então o contador desce uma vez só.
     */
    @Modifying
    @Query(value = """
            UPDATE post_comment SET status = 'REMOVED', body = NULL, removed_at = now()
            WHERE id = :commentId AND status = 'VISIBLE'
            """, nativeQuery = true)
    int removeIfVisible(long commentId);

    @Modifying
    @Query(value = """
            UPDATE post p SET comment_count = p.comment_count - c.n
            FROM (SELECT post_id, count(*) AS n FROM post_comment
                  WHERE author_id = :userId AND status = 'VISIBLE' AND moderation_state IS NULL
                  GROUP BY post_id) c
            WHERE p.id = c.post_id
            """, nativeQuery = true)
    int discountCommentsOf(Long userId);

    @Modifying
    @Query(value = """
            UPDATE post_comment SET status = 'REMOVED', body = NULL, removed_at = now()
            WHERE author_id = :userId AND status = 'VISIBLE'
            """, nativeQuery = true)
    int removeAllCommentsOf(Long userId);
}
