package com.friendlyeats.application.post;

import com.friendlyeats.application.media.MediaJanitor;
import com.friendlyeats.infrastructure.persistence.postgres.PostCommentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Passo "posts e interações" da eliminação da conta (RF5 · LGPD Art. 18, VI), chamado pelo
 * {@code AccountService.eliminate} antes da anonimização, na mesma transação. Idempotente: rodar de novo não muda nada.
 *
 * <ul>
 *   <li>as curtidas da conta saem e os contadores dos posts de terceiros descem; os salvos saem também;</li>
 *   <li>os comentários da conta perdem o texto ({@code REMOVED}) e os contadores descem;</li>
 *   <li>os compartilhamentos perdem o vínculo com a conta, mas o total agregado (que não é pessoal) fica;</li>
 *   <li>os posts da conta viram {@code REMOVED} com o conteúdo apagado e saem do ranking "em alta"; as fotos saem do
 *       bucket depois do commit (o banco perde a referência na mesma transação).</li>
 * </ul>
 */
@Service
public class PostContentEraser {

    private final PostRepository posts;
    private final PostCommentRepository comments;
    private final MediaJanitor media;
    private final TrendingService trending;

    public PostContentEraser(PostRepository posts, PostCommentRepository comments, MediaJanitor media,
                             TrendingService trending) {
        this.posts = posts;
        this.comments = comments;
        this.media = media;
        this.trending = trending;
    }

    @Transactional
    public ErasureReport eraseContentOf(Long userId) {
        List<String> mediaKeys = posts.findMediaKeysByAuthor(userId);
        List<UUID> live = posts.findLiveIdsByAuthor(userId);

        posts.discountLikesOf(userId);
        int reactions = posts.deleteReactionsOf(userId);

        comments.discountCommentsOf(userId);
        int removedComments = comments.removeAllCommentsOf(userId);

        int shares = posts.anonymizeSharesOf(userId);
        int removedPosts = posts.removeAllPostsOf(userId);

        media.deleteAfterCommit(mediaKeys);
        trending.withdraw(live);
        return new ErasureReport(removedPosts, removedComments, reactions, shares, mediaKeys);
    }

    /** Comprovante do passo (sem conteúdo, só contagens e as chaves das fotos apagadas). */
    public record ErasureReport(int postsRemoved, int commentsRemoved, int reactionsDeleted, int sharesAnonymized,
                                List<String> mediaKeysToDelete) {
    }
}
