package com.friendlyeats.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ciclo de vida do post e do comentário (RF9, RF12). */
class PostDomainTest {

    private static Post post(long authorId, PostKind kind) {
        UserAccount author = new UserAccount("Ana", "ana@example.com", "hash", UserCategoryEnum.CRIADOR);
        ReflectionTestUtils.setField(author, "id", authorId);
        return new Post(author, kind, "Título");
    }

    @Test
    void newPostCopiesTheAuthorTypeDerivesTheFeedAndStartsAsDraft() {
        Post post = post(7L, PostKind.FOTO_PRATO);
        assertThat(post.getAuthorUserType()).isEqualTo(UserCategoryEnum.CRIADOR);
        assertThat(post.getFeedType()).isEqualTo(FeedType.POST);
        assertThat(post(7L, PostKind.ALERTA_SANITARIO).getFeedType()).isEqualTo(FeedType.ALERT);
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
        assertThat(post.isVisibleTo(7L)).isTrue();
        assertThat(post.isVisibleTo(8L)).isFalse();
        assertThat(post.isVisibleTo(null)).isFalse();
        assertThat(post.acceptsInteractions()).isFalse();
    }

    @Test
    void transitionsFollowTheLifecycleAndRemovedIsTerminal() {
        assertThat(PostStatus.DRAFT.allowedTransitions()).containsExactlyInAnyOrder(PostStatus.PUBLISHED, PostStatus.REMOVED);
        assertThat(PostStatus.PUBLISHED.allowedTransitions()).containsExactlyInAnyOrder(PostStatus.ARCHIVED, PostStatus.REMOVED);
        assertThat(PostStatus.ARCHIVED.allowedTransitions()).containsExactlyInAnyOrder(PostStatus.PUBLISHED, PostStatus.REMOVED);
        assertThat(PostStatus.REMOVED.allowedTransitions()).isEmpty();

        Post post = post(7L, PostKind.RECEITA);
        assertThatThrownBy(post::archive).isInstanceOf(IllegalStateException.class);
        post.publish();
        assertThat(post.acceptsInteractions()).isTrue();
        post.archive();
        assertThat(post.isVisibleTo(null)).isTrue();
        assertThat(post.acceptsInteractions()).isFalse();
        post.publish();

        post.setBody("texto");
        post.setMediaObjectKey("posts/x.jpg");
        post.setAltText("foto");
        post.setRecipe(new Post.Recipe(List.of("a"), List.of("b"), 1, 1));
        post.setPriceCents(100);
        post.remove();
        assertThat(post.getTitle()).isEqualTo(Post.REMOVED_TITLE);
        assertThat(post.getBody()).isNull();
        assertThat(post.getMediaObjectKey()).isNull();
        assertThat(post.getAltText()).isNull();
        assertThat(post.getRecipe()).isNull();
        assertThat(post.getPriceCents()).isNull();
        assertThat(post.getRemovedAt()).isNotNull();
        assertThat(post.isVisibleTo(7L)).isFalse();
        assertThatThrownBy(post::publish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void alertTakesOnlySharesAndSavesAndExpiryIsByTheDeadline() {
        Post alert = post(7L, PostKind.ALERTA_SANITARIO);
        alert.publish();
        assertThat(alert.acceptsInteractions()).isTrue();
        assertThat(alert.acceptsLikesAndComments()).isFalse();

        Post daily = post(7L, PostKind.PRATO_DO_DIA);
        Instant end = Instant.parse("2026-10-26T02:59:59Z");
        daily.setExpiresAt(end);
        assertThat(daily.isExpired(end.minusSeconds(1))).isFalse();
        assertThat(daily.isExpired(end)).isTrue();
        assertThat(post(7L, PostKind.RECEITA).isExpired(Instant.MAX)).isFalse();
    }

    @Test
    void commentIsTrimmedValidatedAndRemovableByWriterOrPostOwner() {
        Post post = post(7L, PostKind.PERGUNTA);
        UUID postId = post.getId();
        PostComment comment = new PostComment(postId, 9L, "  Na Liberdade!  ");
        assertThat(comment.getBody()).isEqualTo("Na Liberdade!");
        assertThat(comment.canBeRemovedBy(9L, post)).isTrue();
        assertThat(comment.canBeRemovedBy(7L, post)).isTrue();
        assertThat(comment.canBeRemovedBy(10L, post)).isFalse();
        comment.remove();
        assertThat(comment.getBody()).isNull();
        assertThat(comment.isVisible()).isFalse();
        assertThatThrownBy(() -> new PostComment(postId, 9L, "   ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PostComment(postId, 9L, "x".repeat(PostComment.MAX_LENGTH + 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
