package com.friendlyeats.application.post;

import com.friendlyeats.application.post.PostViews.AuthorView;
import com.friendlyeats.domain.model.ShareChannel;

import java.time.Instant;
import java.util.List;

/** Respostas das interações (RF12): curtir, salvar, comentar e compartilhar. */
public final class InteractionViews {

    private InteractionViews() {
    }

    /** {@code canDelete}: quem escreveu ou o dono do post. */
    public record CommentView(Long id, AuthorView author, String body, Instant createdAt, boolean canDelete) {
    }

    /** {@code nextAfter} vai no parâmetro {@code after} da próxima página; {@code null} = fim. */
    public record CommentPageView(List<CommentView> items, Long nextAfter, long total) {
    }

    public record CommentCreatedView(CommentView comment, long comments) {
    }

    public record LikeStateView(boolean liked, long likes) {
    }

    /** Salvar é privado: sem contador público. */
    public record SaveStateView(boolean saved) {
    }

    /** {@code counted} = primeira vez desta conta neste canal (só então o número sobe). */
    public record ShareResultView(String shareUrl, ShareChannel channel, long shares, boolean counted) {
    }
}
