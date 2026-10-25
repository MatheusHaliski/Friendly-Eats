package com.friendlyeats.application.post;

import com.friendlyeats.application.establishment.EstablishmentService;
import com.friendlyeats.application.post.PostReader.Viewer;
import com.friendlyeats.application.post.PostViews.FeedPageView;
import com.friendlyeats.application.post.PostViews.PostDetailView;
import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.persistence.postgres.PostFeedQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Leitura (RF14): a página do post, o feed com filtros e paginação por cursor, os posts sobre um lugar e o ranking
 * "em alta". O feed mostra só posts publicados e não encerrados, de autores ativos com perfil público; para visitante
 * e menor de 18, só os {@code LIVRE}.
 */
@Service
@Transactional(readOnly = true)
public class PostQueryService {

    static final int DEFAULT_PAGE = 20;
    static final int MAX_PAGE = 50;

    private final PostReader reader;
    private final PostFeedQueries feed;
    private final TrendingService trending;
    private final EstablishmentService establishments;

    public PostQueryService(PostReader reader, PostFeedQueries feed, TrendingService trending,
                            EstablishmentService establishments) {
        this.reader = reader;
        this.feed = feed;
        this.trending = trending;
        this.establishments = establishments;
    }

    public PostDetailView getPost(UUID postId, Optional<Long> viewerId) {
        Viewer v = reader.viewer(viewerId);
        return reader.detail(reader.requireVisible(postId, v), v);
    }

    /** {@code GET /api/v1/posts?feed=POST|ALERT&kind=&establishmentId=&cursor=&size=}. */
    public FeedPageView feed(String feedCode, String kindCode, Long establishmentId, String cursor, Integer size,
                             Optional<Long> viewerId) {
        FeedType feedType = feedType(feedCode);
        PostKind kind = kindCode == null || kindCode.isBlank() ? null : PostKind.fromCode(kindCode.strip())
                .orElseThrow(() -> new IllegalArgumentException("Tipo de post desconhecido: " + kindCode + "."));
        if (kind != null && kind.feed() != feedType) {
            throw new IllegalArgumentException(kind.getLabel() + " fica no feed " + kind.feed() + ".");
        }
        return page(feedType, kind, establishmentId, cursor, size, reader.viewer(viewerId));
    }

    /** Posts sobre um lugar: só se a página dele está no ar para quem vê (as regras de 404 e 18+ do RF6). */
    public FeedPageView establishmentFeed(long establishmentId, String kindCode, String cursor, Integer size,
                                          Optional<Long> viewerId) {
        Viewer v = reader.viewer(viewerId);
        establishments.requireVisible(establishmentId, Optional.ofNullable(v.account()));
        PostKind kind = kindCode == null || kindCode.isBlank() ? null : PostKind.fromCode(kindCode.strip())
                .orElseThrow(() -> new IllegalArgumentException("Tipo de post desconhecido: " + kindCode + "."));
        return page(null, kind, establishmentId, cursor, size, v);
    }

    /** Ranking "em alta" do feed, opcionalmente só de um tipo de conta (a cor do autor). */
    public FeedPageView trending(String feedCode, String categoryCode, Integer size, Optional<Long> viewerId) {
        FeedType feedType = feedType(feedCode);
        UserCategoryEnum category = categoryCode == null || categoryCode.isBlank() ? null
                : Arrays.stream(UserCategoryEnum.values()).filter(c -> c.name().equals(categoryCode.strip()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Tipo de conta desconhecido: "
                        + categoryCode + "."));
        int limit = size(size);
        Viewer v = reader.viewer(viewerId);
        List<UUID> ranked = trending.top(feedType, category, limit * 3).stream().map(UUID::fromString).toList();
        Set<UUID> visible = new HashSet<>(feed.visible(ranked, reader.feedViewer(v)));
        List<UUID> ids = ranked.stream().filter(visible::contains).limit(limit).toList();
        return new FeedPageView(reader.cards(reader.inOrder(ids), v), null);
    }

    private FeedPageView page(FeedType feedType, PostKind kind, Long establishmentId, String cursor, Integer size,
                              Viewer v) {
        int limit = size(size);
        FeedCursor c = cursor == null || cursor.isBlank() ? null : FeedCursor.decode(cursor);
        List<UUID> ids = feed.page(new PostFeedQueries.Filter(feedType, kind, establishmentId,
                c == null ? null : c.createdAt(), c == null ? null : c.id()), reader.feedViewer(v), limit + 1);
        List<Post> fetched = reader.inOrder(ids);
        return reader.page(fetched, limit, v);
    }

    static FeedType feedType(String code) {
        if (code == null || code.isBlank()) {
            return FeedType.POST;
        }
        return switch (code.strip().toUpperCase(Locale.ROOT)) {
            case "POST" -> FeedType.POST;
            case "ALERT" -> FeedType.ALERT;
            case "PROFILE" -> throw new IllegalArgumentException("O feed de perfis fica em GET /api/v1/users.");
            default -> throw new IllegalArgumentException("Feed desconhecido: use POST ou ALERT.");
        };
    }

    static int size(Integer size) {
        return size == null ? DEFAULT_PAGE : Math.clamp(size, 1, MAX_PAGE);
    }
}
