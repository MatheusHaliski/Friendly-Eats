package com.friendlyeats.application.post;

import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FeedCursorTest {

    @Test
    void cursorRoundTripsWithMicrosecondPrecisionAndRejectsGarbage() {
        UserAccount ana = new UserAccount("Ana", "ana@example.com", "hash", UserCategoryEnum.COMENSAL);
        ReflectionTestUtils.setField(ana, "id", 7L);
        Post post = new Post(ana, PostKind.RECEITA, "Bolo");
        post.setCreatedAt(Instant.parse("2026-10-25T12:34:56.123456789Z"));

        FeedCursor c = FeedCursor.decode(FeedCursor.encode(post));
        assertThat(c.createdAt()).isEqualTo(Instant.parse("2026-10-25T12:34:56.123456Z"));
        assertThat(c.id()).isEqualTo(post.getId());
        assertThat(FeedCursor.encode(post)).doesNotContain("=", "+", "/");

        assertThatThrownBy(() -> FeedCursor.decode("lixo")).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cursor");
    }

    @Test
    void priceLabelsUseBrazilianReais() {
        assertThat(PostReader.brl(123450)).isEqualTo("R$ 1.234,50");
        assertThat(PostReader.brl(800)).isEqualTo("R$ 8,00");
        assertThat(PostReader.excerpt("a".repeat(400))).hasSize(PostReader.EXCERPT).endsWith("…");
    }
}
