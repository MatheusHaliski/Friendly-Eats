package com.friendlyeats.application.post;

import com.friendlyeats.application.background.BackgroundService;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.common.PublicLinks;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.post.PostViews.AlertView;
import com.friendlyeats.application.post.PostViews.AllergenView;
import com.friendlyeats.application.post.PostViews.AuthorView;
import com.friendlyeats.application.post.PostViews.CountersView;
import com.friendlyeats.application.post.PostViews.FeedPageView;
import com.friendlyeats.application.post.PostViews.InteractionsView;
import com.friendlyeats.application.post.PostViews.KindView;
import com.friendlyeats.application.post.PostViews.PlaceRefView;
import com.friendlyeats.application.post.PostViews.PostCardView;
import com.friendlyeats.application.post.PostViews.PostDetailView;
import com.friendlyeats.application.post.PostViews.PriceView;
import com.friendlyeats.application.post.PostViews.RecipeView;
import com.friendlyeats.application.post.PostViews.ViewerStateView;
import com.friendlyeats.application.safety.SafetyGuard;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.AgeRating;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.ReactionKind;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserPreference;
import com.friendlyeats.domain.taxonomy.Allergen;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostFeedQueries;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Quem vê qual post e como ele aparece (RF14). A regra da página do post é a mesma do feed
 * ({@link PostFeedQueries}): publicado ou arquivado, autor com a conta ativa e o perfil público (o autor sempre se vê)
 * e, se o post é 18+, só com login ({@code 401}) e para maior de 18 ({@code 404} para menor). Nada revela qual regra
 * barrou: inexistente, removido, de perfil privado ou de conta suspensa respondem o mesmo {@code 404}. Também dão
 * {@code 404} o post retirado pela moderação (RF13; o autor ainda abre o seu) e o de quem bloqueou quem vê ou foi
 * bloqueado por ele (RF19).
 */
@Component
public class PostReader {

    static final int EXCERPT = 160;
    static final String ADULTS_ONLY = "Entre na sua conta para ver: conteúdo de bebida alcoólica é só para maiores de 18.";

    /** Quem está vendo: {@code account} nulo = visitante. */
    public record Viewer(UserAccount account, boolean adult) {
        public Long id() {
            return account == null ? null : account.getId();
        }

        public boolean anonymous() {
            return account == null;
        }
    }

    private final PostRepository posts;
    private final EstablishmentRepository establishments;
    private final AuthorDirectory authors;
    private final PublicLinks links;
    private final BackgroundService backgrounds;
    private final SafetyGuard safety;
    private final Clock clock;

    public PostReader(PostRepository posts, EstablishmentRepository establishments, AuthorDirectory authors,
                      PublicLinks links, BackgroundService backgrounds, SafetyGuard safety, Clock clock) {
        this.posts = posts;
        this.establishments = establishments;
        this.authors = authors;
        this.links = links;
        this.backgrounds = backgrounds;
        this.safety = safety;
        this.clock = clock;
    }

    public Viewer viewer(Optional<Long> viewerId) {
        UserAccount account = viewerId.flatMap(authors::find).orElse(null);
        return new Viewer(account, authors.adult(account));
    }

    public PostFeedQueries.Viewer feedViewer(Viewer v) {
        return new PostFeedQueries.Viewer(v.id(), v.adult(), authors.minorBirthAfter(), clock.instant());
    }

    /** O post que {@code v} pode abrir, ou {@code 404}/{@code 401} como na página do post. */
    public Post requireVisible(UUID postId, Viewer v) {
        Post post = posts.findById(postId).filter(p -> p.isVisibleTo(v.id())).orElseThrow(PostReader::notFound);
        if (post.isAuthoredBy(v.id())) {
            return post;
        }
        if (post.isModerated() || safety.blockedEitherWay(v.id(), post.getAuthorId())) {
            throw notFound();
        }
        UserAccount author = authors.find(post.getAuthorId()).filter(AuthorDirectory::isActive)
                .orElseThrow(PostReader::notFound);
        if (authors.isPrivate(author)) {
            throw notFound();
        }
        if (post.getAgeRating() == AgeRating.AGE_18) {
            if (v.anonymous()) {
                throw new CommonExceptions.Unauthenticated(ADULTS_ONLY);
            }
            if (!v.adult()) {
                throw notFound();
            }
        }
        return post;
    }

    static CommonExceptions.NotFound notFound() {
        return new CommonExceptions.NotFound("Post não encontrado.");
    }

    // ------------------------------------------------------------ página do post

    public PostDetailView detail(Post p, Viewer v) {
        UserAccount author = authors.find(p.getAuthorId()).orElse(null);
        PlaceRefView place = placeOf(p, places(List.of(p), v));
        ViewerStateView state = v.anonymous() ? ViewerStateView.ANONYMOUS : new ViewerStateView(
                posts.existsReaction(p.getId(), v.id(), ReactionKind.LIKE.name()),
                posts.existsReaction(p.getId(), v.id(), ReactionKind.SAVE.name()), p.isAuthoredBy(v.id()));
        UserPreference.CommentAudience audience = author == null ? UserPreference.CommentAudience.NOBODY
                : authors.preferenceOf(author).getWhoCanComment();
        CommentPolicy.Decision comment = CommentPolicy.decide(p, audience, v.account());
        InteractionsView interactions = new InteractionsView(p.acceptsInteractions(), p.acceptsLikesAndComments(),
                p.acceptsLikesAndComments(), p.acceptsInteractions(), true, audience.name(), comment.allowed(),
                comment.reason());
        boolean dietaryNotice = p.getDietaryStyles().contains(DietaryStyle.SEM_GLUTEN.name())
                || p.getDietaryStyles().contains(DietaryStyle.SEM_LACTOSE.name());
        String notice = !p.getAllergens().isEmpty() || !p.getFoodWarnings().isEmpty() || dietaryNotice
                ? Allergen.NOTICE : null;
        return new PostDetailView(p.getId(), kind(p.getKind()), p.getFeedType(), p.getMediaType(), p.getTitle(),
                p.getBody(), links.mediaUrl(p.getMediaObjectKey()), p.getAltText(), place, cuisine(p), format(p),
                dietary(p), allergens(p), warnings(p), notice, p.getAgeRating(), p.getAgeRating().getLabel(),
                p.isAdvertising(), p.isAdvertising() ? "Publicidade" : null, p.getStartsAt(), p.getEndsAt(), price(p),
                recipe(p), alert(p), p.getCity(), p.getExpiresAt(), p.isExpired(clock.instant()), p.getStatus(),
                p.getCreatedAt(), p.getUpdatedAt(), authors.view(author), counters(p), state, interactions,
                links.postShareUrl(p.getId()), backgrounds.resolve(p.getBackground()));
    }

    // ------------------------------------------------------------ cards

    /** Pede {@code size + 1} posts e recebe aqui: sobra um = há próxima página (sem COUNT). */
    public FeedPageView page(List<Post> fetched, int size, Viewer v) {
        boolean hasMore = fetched.size() > size;
        List<Post> items = hasMore ? fetched.subList(0, size) : fetched;
        return new FeedPageView(cards(items, v), hasMore ? FeedCursor.encode(items.getLast()) : null);
    }

    public List<PostCardView> cards(List<Post> items, Viewer v) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = items.stream().map(Post::getId).toList();
        Set<UUID> liked = reacted(v, ReactionKind.LIKE, ids);
        Set<UUID> saved = reacted(v, ReactionKind.SAVE, ids);
        Map<Long, UserAccount> byId = authors.findAll(items.stream().map(Post::getAuthorId).distinct().toList());
        Map<Long, PlaceRefView> places = places(items, v);
        return items.stream().map(p -> new PostCardView(p.getId(), kind(p.getKind()), p.getFeedType(),
                p.getMediaType(), p.getTitle(), excerpt(p.getBody()), links.mediaUrl(p.getMediaObjectKey()),
                p.getAltText(), placeOf(p, places), cuisine(p), format(p), dietary(p), allergens(p),
                warnings(p), p.getAgeRating(), p.getAgeRating().getLabel(), p.isAdvertising(),
                p.isAdvertising() ? "Publicidade" : null, p.getStartsAt(), p.getEndsAt(), price(p), alert(p),
                p.getExpiresAt(), p.isExpired(clock.instant()), p.getStatus(), p.getCreatedAt(),
                authors.view(byId.get(p.getAuthorId())), counters(p),
                new ViewerStateView(liked.contains(p.getId()), saved.contains(p.getId()), p.isAuthoredBy(v.id())),
                PostViews.postPath(p.getId()), backgrounds.resolve(p.getBackground()))).toList();
    }

    /** Carrega os posts na ordem dos ids (o {@code findAllById} não garante ordem). */
    public List<Post> inOrder(List<UUID> ids) {
        Map<UUID, Post> byId = posts.findAllById(ids).stream()
                .collect(Collectors.toMap(Post::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    private Set<UUID> reacted(Viewer v, ReactionKind kind, List<UUID> postIds) {
        if (v.anonymous() || postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(posts.findReactedPostIds(v.id(), kind.name(), postIds));
    }

    /**
     * Lugares citados, só os que estão no ar para quem vê: conta dona ativa e com selo, lugar não removido (o dono
     * sempre vê o seu) e, se for de bebida alcoólica, só para maior de 18.
     */
    private Map<Long, PlaceRefView> places(Collection<Post> items, Viewer v) {
        List<Long> ids = items.stream().map(Post::getEstablishmentId).filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Establishment> found = establishments.findAllById(ids);
        Map<Long, UserAccount> owners = authors.findAll(found.stream().map(Establishment::getOwnerId).distinct().toList());
        return found.stream().filter(e -> placeVisible(e, owners.get(e.getOwnerId()), v))
                .collect(Collectors.toMap(Establishment::getId, e -> new PlaceRefView(e.getId(), e.getName(),
                        e.getCity(), e.getUf(), e.isAlcoholFocused(), PostViews.establishmentPath(e.getId()))));
    }

    private static PlaceRefView placeOf(Post p, Map<Long, PlaceRefView> places) {
        return p.getEstablishmentId() == null ? null : places.get(p.getEstablishmentId());
    }

    private static boolean placeVisible(Establishment e, UserAccount owner, Viewer v) {
        if (owner != null && Objects.equals(owner.getId(), v.id())) {
            return true;
        }
        boolean published = e.getStatus() != Establishment.Status.REMOVED && owner != null
                && owner.getAccountStatus() == AccountStatus.ACTIVE
                && owner.getCategoryVerification() == CategoryVerification.VERIFIED;
        return published && (!e.isAlcoholFocused() || v.adult());
    }

    // ------------------------------------------------------------ partes

    static KindView kind(PostKind k) {
        return new KindView(k.name(), k.getLabel(), k.getLucideIcon(), k.getBorder());
    }

    private static Coded cuisine(Post p) {
        return Cuisine.fromCode(p.getCuisine()).map(c -> new Coded(c.name(), c.getLabel(), c.getMacro().name()))
                .orElse(null);
    }

    private static Coded format(Post p) {
        return FoodFormat.fromCode(p.getFoodFormat())
                .map(f -> new Coded(f.name(), f.getLabel(), f.getFamily().name())).orElse(null);
    }

    private static List<Coded> dietary(Post p) {
        return p.getDietaryStyles().stream().map(DietaryStyle::fromCode).flatMap(Optional::stream)
                .map(s -> new Coded(s.name(), s.getLabel(), null)).toList();
    }

    private static List<AllergenView> allergens(Post p) {
        return p.getAllergens().entrySet().stream().flatMap(e -> Allergen.fromCode(e.getKey()).stream()
                        .map(a -> {
                            Allergen.Level level = Allergen.Level.valueOf(e.getValue());
                            return new AllergenView(a.name(), a.getLabel(), level.name(), level.getLabel());
                        }))
                .sorted(java.util.Comparator.comparing(AllergenView::code)).toList();
    }

    private static List<Coded> warnings(Post p) {
        return p.getFoodWarnings().stream().map(Allergen::fromCode).flatMap(Optional::stream)
                .map(a -> new Coded(a.name(), a.getLabel(), a.getKind().name())).toList();
    }

    static PriceView price(Post p) {
        if (p.getPriceFromCents() != null && p.getPriceToCents() != null) {
            return new PriceView(null, p.getPriceFromCents(), p.getPriceToCents(), null, false,
                    "De " + brl(p.getPriceFromCents()) + " por " + brl(p.getPriceToCents()));
        }
        if (p.getDiscountPercent() != null) {
            return new PriceView(null, null, null, p.getDiscountPercent(), false, p.getDiscountPercent() + "% de desconto");
        }
        if (Boolean.TRUE.equals(p.getFreeEntry())) {
            return new PriceView(null, null, null, null, true, "Gratuito");
        }
        if (p.getPriceCents() != null) {
            return new PriceView(p.getPriceCents(), null, null, null, false, brl(p.getPriceCents()));
        }
        return null;
    }

    /** "R$ 1.234,50": o espaço é comum (não o NBSP do NumberFormat), para o leitor de tela e os testes. */
    static String brl(int cents) {
        return "R$ " + String.format(Locale.forLanguageTag("pt-BR"), "%,.2f", cents / 100.0);
    }

    private static RecipeView recipe(Post p) {
        Post.Recipe r = p.getRecipe();
        return r == null ? null : new RecipeView(r.ingredients(), r.steps(), r.servings(), r.prepMinutes());
    }

    private static AlertView alert(Post p) {
        Post.FoodAlert a = p.getFoodAlert();
        if (a == null) {
            return null;
        }
        String host;
        try {
            host = URI.create(a.sourceUrl()).getHost();
        } catch (RuntimeException e) {
            host = null;
        }
        return new AlertView(a.product(), a.brand(), a.lots(), a.reason(), a.sourceUrl(), host);
    }

    private static CountersView counters(Post p) {
        return new CountersView(p.getLikeCount(), p.getCommentCount(), p.getShareCount());
    }

    static String excerpt(String body) {
        if (body == null || body.length() <= EXCERPT) {
            return body;
        }
        return body.substring(0, EXCERPT - 1).stripTrailing() + "…";
    }

    /** Usado pelo AuthorView nos comentários (RF12) sem expor o diretório. */
    public AuthorView author(UserAccount user) {
        return authors.view(user);
    }
}
