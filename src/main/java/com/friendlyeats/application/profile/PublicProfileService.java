package com.friendlyeats.application.profile;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.common.PublicLinks;
import com.friendlyeats.application.post.AuthorDirectory;
import com.friendlyeats.application.post.FeedCursor;
import com.friendlyeats.application.post.PostReader;
import com.friendlyeats.application.post.PostViews.FeedPageView;
import com.friendlyeats.application.profile.ProfileViews.ProfileCardView;
import com.friendlyeats.application.profile.ProfileViews.ProfilePageView;
import com.friendlyeats.application.profile.ProfileViews.PublicProfileView;
import com.friendlyeats.application.safety.SafetyGuard;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostStatus;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ProfileDirectoryQueries;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Perfil público, posts do autor e feed de perfis (RF15). O perfil abre só para conta ativa (senão {@code 404}, sem
 * dizer o motivo); perfil privado mostra o mínimo e fecha os posts para quem não é o titular. Menor de 16 tem o perfil
 * privado travado (ECA Digital), então nunca entra no feed de perfis. Bloqueio (RF19) vale nos dois sentidos: o perfil
 * e os posts respondem {@code 404} e a conta some da busca de perfis, sem revelar o bloqueio.
 */
@Service
@Transactional(readOnly = true)
public class PublicProfileService {

    static final int DEFAULT_PAGE = 20;
    static final int MAX_PAGE = 50;

    private final AuthorDirectory authors;
    private final PostReader reader;
    private final PostRepository posts;
    private final ProfileDirectoryQueries directory;
    private final PublicLinks links;
    private final SafetyGuard safety;

    public PublicProfileService(AuthorDirectory authors, PostReader reader, PostRepository posts,
                                ProfileDirectoryQueries directory, PublicLinks links, SafetyGuard safety) {
        this.authors = authors;
        this.reader = reader;
        this.posts = posts;
        this.directory = directory;
        this.links = links;
        this.safety = safety;
    }

    public PublicProfileView profile(long userId, Optional<Long> viewerId) {
        UserAccount u = visibleAccount(userId, viewerId);
        boolean self = viewerId.filter(id -> id == userId).isPresent();
        AccountBadge badge = AccountBadge.of(u.getUserType());
        boolean verified = u.getCategoryVerification() == CategoryVerification.VERIFIED;
        if (!self && authors.isPrivate(u)) {
            return new PublicProfileView(u.getId(), u.getName(), u.getHandle(), badge, verified, true, null, null,
                    null, null, null, null, null, null, null, null);
        }
        return new PublicProfileView(u.getId(), u.getName(), u.getHandle(), badge, verified, authors.isPrivate(u),
                links.mediaUrl(u.getPhotoObjectKey()), u.getBio(), u.getPronouns(), u.getLinks(), city(u), uf(u),
                u.getCreatedAt(), posts.countByAuthorIdAndStatusInAndModerationStateIsNull(u.getId(), PostStatus.PUBLIC),
                "/api/v1/users/" + u.getId() + "/posts", links.profileShareUrl(u.getId()));
    }

    /** Publicados e arquivados (com os encerrados marcados), dos mais novos aos mais antigos; 18+ só para maior. */
    public FeedPageView posts(long userId, String cursor, Integer size, Optional<Long> viewerId) {
        UserAccount u = visibleAccount(userId, viewerId);
        boolean self = viewerId.filter(id -> id == userId).isPresent();
        if (!self && authors.isPrivate(u)) {
            throw new CommonExceptions.Forbidden("Este perfil é privado: os posts aparecem só para o titular.");
        }
        PostReader.Viewer v = reader.viewer(viewerId);
        boolean adult = self || v.adult();
        int limit = size(size);
        // o cursor só escolhe a página: o acesso (conta ativa, perfil privado) já foi conferido acima
        FeedCursor after = cursor == null || cursor.isBlank() ? null : FeedCursor.decode(cursor);
        List<Post> fetched = after == null
                ? posts.findProfileFeed(userId, adult, limit + 1)
                : posts.findProfileFeedBefore(userId, adult, after.createdAt(), after.id(), limit + 1);
        return reader.page(fetched, limit, v);
    }

    /** {@code GET /api/v1/users?type=&city=&q=&cursor=&size=}: o feed de perfis ({@code FeedType.PROFILE}). */
    public ProfilePageView profiles(String typeCode, String city, String q, String cursor, Integer size,
                                    Optional<Long> viewerId) {
        UserCategoryEnum type = typeCode == null || typeCode.isBlank() ? null
                : Arrays.stream(UserCategoryEnum.values()).filter(c -> c.name().equals(typeCode.strip())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de conta desconhecido: " + typeCode + "."));
        String c = city == null || city.isBlank() ? null : city.strip();
        String text = q == null || q.isBlank() ? null : q.strip();
        if (text != null && text.length() > 80) {
            throw new IllegalArgumentException("A busca por nome vai até 80 caracteres.");
        }
        int limit = size(size);
        List<Long> ids = directory.page(new ProfileDirectoryQueries.Filter(type, c, text, decode(cursor)),
                authors.minorBirthAfter(), viewerId.orElse(null), limit + 1);
        boolean hasMore = ids.size() > limit;
        List<Long> page = hasMore ? ids.subList(0, limit) : ids;
        Map<Long, UserAccount> byId = authors.findAll(page);
        List<ProfileCardView> cards = page.stream().map(byId::get).filter(Objects::nonNull)
                .map(u -> new ProfileCardView(u.getId(), u.getName(), u.getHandle(), AccountBadge.of(u.getUserType()),
                        u.getCategoryVerification() == CategoryVerification.VERIFIED,
                        links.mediaUrl(u.getPhotoObjectKey()), u.getBio(), city(u), uf(u),
                        "/api/v1/users/" + u.getId() + "/profile"))
                .toList();
        return new ProfilePageView(cards, hasMore ? encode(page.getLast()) : null);
    }

    private UserAccount visibleAccount(long userId, Optional<Long> viewerId) {
        boolean self = viewerId.filter(id -> id == userId).isPresent();
        return authors.find(userId).filter(u -> self || AuthorDirectory.isActive(u))
                .filter(u -> self || !safety.blockedEitherWay(viewerId.orElse(null), userId))
                .orElseThrow(() -> new CommonExceptions.NotFound("Perfil não encontrado."));
    }

    /** Cidade de atuação (cadastro do tipo de conta); a do comensal é pessoal e não sai. */
    static String city(UserAccount u) {
        return u.getUserType() == UserCategoryEnum.COMENSAL ? null : detail(u, "city");
    }

    static String uf(UserAccount u) {
        return u.getUserType() == UserCategoryEnum.COMENSAL ? null : detail(u, "state");
    }

    private static String detail(UserAccount u, String key) {
        Object v = u.getTypeDetails() == null ? null : u.getTypeDetails().get(key);
        return v == null ? null : v.toString();
    }

    static String encode(long lastId) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(("u:" + lastId).getBytes(StandardCharsets.UTF_8));
    }

    static Long decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            if (!raw.startsWith("u:")) {
                throw new IllegalArgumentException("prefixo");
            }
            return Long.parseLong(raw.substring(2));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Cursor de paginação inválido.", e);
        }
    }

    static int size(Integer size) {
        return size == null ? DEFAULT_PAGE : Math.clamp(size, 1, MAX_PAGE);
    }
}
