package com.friendlyeats.application.post;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.common.PublicLinks;
import com.friendlyeats.application.post.InteractionViews.CommentCreatedView;
import com.friendlyeats.application.post.InteractionViews.CommentPageView;
import com.friendlyeats.application.post.InteractionViews.CommentView;
import com.friendlyeats.application.post.InteractionViews.LikeStateView;
import com.friendlyeats.application.post.InteractionViews.SaveStateView;
import com.friendlyeats.application.post.InteractionViews.ShareResultView;
import com.friendlyeats.application.post.PostReader.Viewer;
import com.friendlyeats.application.safety.SafetyGuard;
import com.friendlyeats.application.safety.WordFilter;
import com.friendlyeats.domain.model.CommentStatus;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostComment;
import com.friendlyeats.domain.model.ReactionKind;
import com.friendlyeats.domain.model.ShareChannel;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.cache.redis.TrendingScoreCalculator.InteractionType;
import com.friendlyeats.infrastructure.persistence.postgres.PostCommentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * RF12 · interações com contagem: curtir, salvar, comentar (caixa de mensagens) e compartilhar.
 *
 * <p>Regra dos contadores: a linha de associação e o contador mudam na mesma transação, e o contador só se move quando
 * a linha realmente entrou ou saiu. Repetir "curtir" ou o mesmo compartilhamento não altera o número (idempotente,
 * seguro para o app repetir). O post precisa estar visível para quem interage, com as regras da página do post (RF14).
 * Alerta sanitário aceita só compartilhar e salvar; comentar segue o {@link CommentPolicy}.
 *
 * <p>Na lista de comentários (RF19) somem, para quem vê, os de quem o bloqueou, foi bloqueado ou silenciado por ele, e
 * para todos os que têm palavras filtradas por quem publicou (o autor do comentário ainda vê o seu). Comentários
 * retirados pela moderação (RF13) não aparecem e não contam.
 */
@Service
@Transactional
public class PostInteractionService {

    static final int MAX_COMMENTS_PAGE = 50;

    private final PostRepository posts;
    private final PostCommentRepository comments;
    private final PostReader reader;
    private final AuthorDirectory authors;
    private final PublicLinks links;
    private final TrendingService trending;
    private final SafetyGuard safety;

    public PostInteractionService(PostRepository posts, PostCommentRepository comments, PostReader reader,
                                  AuthorDirectory authors, PublicLinks links, TrendingService trending,
                                  SafetyGuard safety) {
        this.posts = posts;
        this.comments = comments;
        this.reader = reader;
        this.authors = authors;
        this.links = links;
        this.trending = trending;
        this.safety = safety;
    }

    // ------------------------------------------------------------ curtir

    public LikeStateView like(UUID postId, long viewerId) {
        Post post = openPost(postId, viewerId);
        if (!post.acceptsLikesAndComments()) {
            throw new CommonExceptions.Conflict("Alerta sanitário não recebe curtidas nem comentários: compartilhe "
                    + "ou salve.");
        }
        if (posts.insertReaction(post.getId(), viewerId, ReactionKind.LIKE.name()) == 1) {
            posts.addToLikeCount(post.getId(), 1);
            trending.interaction(post, InteractionType.LIKE, 1);
        }
        return new LikeStateView(true, posts.findCounters(post.getId()).getLikes());
    }

    /** Descurtir continua permitido em post arquivado: a pessoa sempre pode desfazer o que fez. */
    public LikeStateView unlike(UUID postId, long viewerId) {
        Post post = visiblePost(postId, requireActive(viewerId));
        if (posts.deleteReaction(post.getId(), viewerId, ReactionKind.LIKE.name()) == 1) {
            posts.addToLikeCount(post.getId(), -1);
            trending.interaction(post, InteractionType.LIKE, -1);
        }
        return new LikeStateView(false, posts.findCounters(post.getId()).getLikes());
    }

    // ------------------------------------------------------------ salvar (privado, sem contador público)

    public SaveStateView save(UUID postId, long viewerId) {
        Post post = visiblePost(postId, requireActive(viewerId));
        if (posts.insertReaction(post.getId(), viewerId, ReactionKind.SAVE.name()) == 1) {
            trending.interaction(post, InteractionType.SAVE, 1);
        }
        return new SaveStateView(true);
    }

    /** Tirar dos salvos sempre funciona, mesmo que o post não esteja mais visível. */
    public SaveStateView unsave(UUID postId, long viewerId) {
        posts.deleteReaction(postId, viewerId, ReactionKind.SAVE.name());
        return new SaveStateView(false);
    }

    // ------------------------------------------------------------ comentar

    public CommentCreatedView addComment(UUID postId, long viewerId, String body) {
        UserAccount viewer = authors.find(viewerId).orElseThrow(CommonExceptions.Unauthenticated::new);
        Post post = visiblePost(postId, new Viewer(viewer, authors.adult(viewer)));
        UserAccount author = authors.find(post.getAuthorId()).orElseThrow(PostReader::notFound);
        CommentPolicy.Decision d = CommentPolicy.decide(post, authors.preferenceOf(author).getWhoCanComment(), viewer);
        if (!d.allowed()) {
            throw switch (d.status()) {
                case 409 -> new CommonExceptions.Conflict(d.reason());
                case 401 -> new CommonExceptions.Unauthenticated(d.reason());
                default -> new CommonExceptions.Forbidden(d.reason());
            };
        }
        PostComment comment = comments.saveAndFlush(new PostComment(post.getId(), viewerId, body));
        posts.addToCommentCount(post.getId(), 1);
        trending.interaction(post, InteractionType.COMMENT, 1);
        return new CommentCreatedView(new CommentView(comment.getId(), authors.view(viewer), comment.getBody(),
                comment.getCreatedAt(), true), posts.findCounters(post.getId()).getComments());
    }

    public void removeComment(UUID postId, long commentId, long viewerId) {
        Post post = visiblePost(postId, requireActive(viewerId));
        PostComment comment = comments.findById(commentId)
                .filter(c -> c.getPostId().equals(postId) && c.isVisible())
                .orElseThrow(() -> new CommonExceptions.NotFound("Comentário não encontrado."));
        if (!comment.canBeRemovedBy(viewerId, post)) {
            throw new CommonExceptions.Forbidden("Só quem escreveu ou o dono do post pode apagar este comentário.");
        }
        if (comments.removeIfVisible(commentId) == 0) {
            // outra exclusão chegou antes: o contador já desceu
            throw new CommonExceptions.NotFound("Comentário não encontrado.");
        }
        posts.addToCommentCount(post.getId(), -1);
        trending.interaction(post, InteractionType.COMMENT, -1);
    }

    @Transactional(readOnly = true)
    public CommentPageView listComments(UUID postId, long afterId, int limit, Optional<Long> viewerId) {
        Viewer v = reader.viewer(viewerId);
        Post post = reader.requireVisible(postId, v);
        int size = Math.clamp(limit, 1, MAX_COMMENTS_PAGE);
        List<PostComment> page = comments.findByPostIdAndStatusAndModerationStateIsNullAndIdGreaterThanOrderByIdAsc(
                postId, CommentStatus.VISIBLE, afterId, Limit.of(size + 1));
        boolean hasMore = page.size() > size;
        List<PostComment> items = hasMore ? page.subList(0, size) : page;
        Long nextAfter = hasMore ? items.getLast().getId() : null;
        Set<Long> hidden = safety.hiddenFrom(v.id());
        List<String> filtered = authors.find(post.getAuthorId()).map(safety::filterOf).orElse(List.of());
        items = items.stream()
                .filter(c -> !hidden.contains(c.getAuthorId()))
                .filter(c -> c.getAuthorId().equals(v.id()) || post.isAuthoredBy(c.getAuthorId())
                        || !WordFilter.matches(c.getBody(), filtered))
                .toList();
        Map<Long, UserAccount> byId = authors.findAll(items.stream().map(PostComment::getAuthorId).distinct().toList());
        List<CommentView> views = items.stream().map(c -> new CommentView(c.getId(),
                authors.view(byId.get(c.getAuthorId())), c.getBody(), c.getCreatedAt(),
                v.id() != null && c.canBeRemovedBy(v.id(), post))).toList();
        return new CommentPageView(views, nextAfter, posts.findCounters(postId).getComments());
    }

    // ------------------------------------------------------------ compartilhar

    public ShareResultView share(UUID postId, long viewerId, ShareChannel channel) {
        Post post = openPost(postId, viewerId);
        boolean counted = posts.insertShare(post.getId(), viewerId, channel.name()) == 1;
        if (counted) {
            posts.addToShareCount(post.getId(), 1);
            trending.interaction(post, InteractionType.SHARE, 1);
        }
        return new ShareResultView(links.postShareUrl(post.getId()), channel,
                posts.findCounters(post.getId()).getShares(), counted);
    }

    // ------------------------------------------------------------ regras comuns

    private Post visiblePost(UUID postId, Viewer v) {
        return reader.requireVisible(postId, v);
    }

    /** Para escrever: conta ativa, post visível e publicado (arquivado responde 409). */
    private Post openPost(UUID postId, long viewerId) {
        Post post = visiblePost(postId, requireActive(viewerId));
        if (!post.acceptsInteractions()) {
            throw new CommonExceptions.Conflict("Este post está arquivado e não recebe mais curtidas, comentários ou "
                    + "compartilhamentos.");
        }
        return post;
    }

    private Viewer requireActive(long viewerId) {
        UserAccount viewer = authors.find(viewerId).filter(AuthorDirectory::isActive)
                .orElseThrow(() -> new CommonExceptions.Forbidden(
                        "Sua conta precisa estar ativa para interagir. Confirme seu e-mail ou fale com o suporte."));
        return new Viewer(viewer, authors.adult(viewer));
    }
}
