package com.friendlyeats.application.post;

import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.background.BackgroundValidator;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentService;
import com.friendlyeats.application.media.ImageSanitizer;
import com.friendlyeats.application.media.MediaExceptions;
import com.friendlyeats.application.media.MediaUploads;
import com.friendlyeats.application.post.PostDrafts.AlertDraft;
import com.friendlyeats.application.post.PostDrafts.PostDraft;
import com.friendlyeats.application.post.PostDrafts.RecipeDraft;
import com.friendlyeats.application.post.PostViews.PostDetailView;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.MediaType;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.PostStatus;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Publicar, editar, arquivar, restaurar e apagar o próprio post de comida (RF9).
 *
 * <ul>
 *   <li>Quem publica: conta ativa; organizações só depois do selo; cada tipo tem os seus autores
 *       ({@link PostKind#denialFor}); prato do dia e promoção do dono só sobre um lugar da própria conta.</li>
 *   <li>O lugar citado precisa estar visível para o autor (as regras do RF6, inclusive 18+).</li>
 *   <li>A foto chega antes por URL pré-assinada e é limpa aqui ({@link MediaUploads}, sem EXIF/GPS); a versão
 *       pública vai para {@code posts/} e a anterior sai do bucket depois do commit.</li>
 *   <li>A moldura (RF16) é validada pelo {@link BackgroundValidator}; alerta sanitário não tem moldura.</li>
 * </ul>
 */
@Service
@Transactional
public class PostPublishingService {

    /** Campos que o PATCH aceita; o tipo nunca muda (apague e publique outro). */
    static final Set<String> EDITABLE = Set.of("title", "body", "mediaObjectKey", "altText", "establishmentId",
            "cuisine", "foodFormat", "dietaryStyles", "allergens", "warnings", "ageRating", "advertising", "startsAt",
            "endsAt", "priceCents", "priceFromCents", "priceToCents", "discountPercent", "free", "recipe", "alert",
            "city", "background");

    private final PostRepository posts;
    private final UserAccountRepository users;
    private final EstablishmentRepository establishmentRows;
    private final EstablishmentService establishments;
    private final AgePolicy ages;
    private final MediaUploads media;
    private final BackgroundValidator backgrounds;
    private final PostReader reader;
    private final TrendingService trending;
    private final JsonMapper json;
    private final Clock clock;

    public PostPublishingService(PostRepository posts, UserAccountRepository users,
                                 EstablishmentRepository establishmentRows, EstablishmentService establishments,
                                 AgePolicy ages, MediaUploads media, BackgroundValidator backgrounds, PostReader reader,
                                 TrendingService trending, JsonMapper json, Clock clock) {
        this.posts = posts;
        this.users = users;
        this.establishmentRows = establishmentRows;
        this.establishments = establishments;
        this.ages = ages;
        this.media = media;
        this.backgrounds = backgrounds;
        this.reader = reader;
        this.trending = trending;
        this.json = json;
        this.clock = clock;
    }

    public PostDetailView create(long viewerId, PostDraft d) {
        UserAccount author = requireActive(viewerId);
        List<String> errors = new ArrayList<>();
        PostKind kind = PostRules.kind(d.kind(), errors);
        if (kind == null) {
            throw new CommonExceptions.Invalid(errors);
        }
        requireKind(kind, author);
        Instant now = clock.instant();
        Establishment place = place(kind, author, d.establishmentId(), null, errors);
        String uploadKey = PostRules.text(d.mediaObjectKey(), "mediaObjectKey", 512, errors);
        PostRules.Content c = PostRules.validate(kind, d, new PostRules.Context(author.getUserType(),
                ages.adult(author.getUserDateOfBirth()), place != null && place.isAlcoholFocused(), uploadKey != null,
                now, null, null), errors);
        Map<String, Object> background = background(d.background(), kind, author, errors);
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        // a foto só é processada quando o resto já passou: limpar imagem custa mais que validar texto
        String photo = uploadKey == null ? null : claimPhoto(author.getId(), uploadKey);
        Post post = new Post(author, kind, c.title());
        post.setCreatedAt(now);
        apply(post, c, place, photo);
        post.setBackground(background);
        post.publish();
        posts.saveAndFlush(post);
        trending.published(post);
        return reader.detail(post, reader.viewer(Optional.of(viewerId)));
    }

    /** PATCH parcial: chave ausente mantém; {@code null} limpa (ex.: {@code "background": null} = moldura padrão). */
    public PostDetailView update(long viewerId, UUID postId, Map<String, Object> patch) {
        Post post = ownPost(viewerId, postId);
        UserAccount author = requireActive(viewerId);
        requireKind(post.getKind(), author);
        List<String> errors = new ArrayList<>();
        for (String key : patch.keySet()) {
            if (!EDITABLE.contains(key)) {
                errors.add("Campo \"" + key + "\" não pode ser alterado por aqui"
                        + ("kind".equals(key) ? ": para mudar o tipo, apague e publique outro." : "."));
            }
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        PostDraft d = merge(post, patch);
        Establishment place = place(post.getKind(), author, d.establishmentId(), post.getEstablishmentId(), errors);
        String current = post.getMediaObjectKey();
        String requested = PostRules.text(d.mediaObjectKey(), "mediaObjectKey", 512, errors);
        PostRules.Content c = PostRules.validate(post.getKind(), d, new PostRules.Context(author.getUserType(),
                ages.adult(author.getUserDateOfBirth()), place != null && place.isAlcoholFocused(), requested != null,
                clock.instant(), post.getCreatedAt(), post.getEndsAt()), errors);
        Map<String, Object> background = patch.containsKey("background")
                ? background(patch.get("background"), post.getKind(), author, errors)
                : post.getBackground();
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        String photo = Objects.equals(requested, current) ? current
                : requested == null ? null : claimPhoto(author.getId(), requested);
        if (current != null && !Objects.equals(photo, current)) {
            media.discardAfterCommit(current);
        }
        apply(post, c, place, photo);
        post.setBackground(background);
        posts.saveAndFlush(post);
        return reader.detail(post, reader.viewer(Optional.of(viewerId)));
    }

    public PostDetailView archive(long viewerId, UUID postId) {
        Post post = ownPost(viewerId, postId);
        if (post.getStatus() != PostStatus.PUBLISHED) {
            throw new CommonExceptions.Conflict("Só posts publicados podem ser arquivados.");
        }
        post.archive();
        posts.saveAndFlush(post);
        trending.withdraw(List.of(post.getId()));
        return reader.detail(post, reader.viewer(Optional.of(viewerId)));
    }

    public PostDetailView restore(long viewerId, UUID postId) {
        Post post = ownPost(viewerId, postId);
        if (post.getStatus() != PostStatus.ARCHIVED) {
            throw new CommonExceptions.Conflict("Só posts arquivados podem voltar ao feed.");
        }
        requireKind(post.getKind(), requireActive(viewerId));
        post.publish();
        posts.saveAndFlush(post);
        trending.published(post);
        return reader.detail(post, reader.viewer(Optional.of(viewerId)));
    }

    /**
     * Apagar pelo autor: o conteúdo some (o título vira "Conteúdo removido") e o post responde 404 para todos. A foto
     * sai do bucket depois do commit.
     */
    public void remove(long viewerId, UUID postId) {
        Post post = ownPost(viewerId, postId);
        String photo = post.getMediaObjectKey();
        post.remove();
        posts.saveAndFlush(post);
        media.discardAfterCommit(photo);
        trending.withdraw(List.of(post.getId()));
    }

    // ------------------------------------------------------------ regras com o banco

    private UserAccount requireActive(long viewerId) {
        UserAccount u = users.findById(viewerId).orElseThrow(CommonExceptions.Unauthenticated::new);
        if (u.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new CommonExceptions.Forbidden(
                    "Sua conta precisa estar ativa para publicar. Confirme seu e-mail ou fale com o suporte.");
        }
        return u;
    }

    private static void requireKind(PostKind kind, UserAccount author) {
        String denial = kind.denialFor(author.getUserType(), author.getCategoryVerification());
        if (denial != null) {
            throw new CommonExceptions.Forbidden(denial);
        }
    }

    /** Post de outra pessoa, apagado ou inexistente: 404 sem dizer qual. */
    private Post ownPost(long viewerId, UUID postId) {
        return posts.findById(postId)
                .filter(p -> p.getStatus() != PostStatus.REMOVED)
                .filter(p -> p.isAuthoredBy(viewerId))
                .orElseThrow(PostReader::notFound);
    }

    /**
     * O lugar citado: um lugar novo precisa estar visível para o autor (RF6, inclusive 18+); o que já estava no post
     * continua valendo. Prato do dia e promoção do dono exigem um lugar da própria conta, aberto.
     */
    private Establishment place(PostKind kind, UserAccount author, Long id, Long previousId, List<String> errors) {
        Establishment place = null;
        if (id != null && id.equals(previousId)) {
            place = establishmentRows.findById(id).orElse(null);
        } else if (id != null) {
            try {
                place = establishments.requireVisible(id, Optional.of(author));
            } catch (CommonExceptions.NotFound | CommonExceptions.Unauthenticated e) {
                errors.add("Estabelecimento não encontrado (\"establishmentId\": " + id + ").");
            }
        }
        if (kind.needsOwnedPlace(author.getUserType())) {
            if (id == null) {
                errors.add("Informe o lugar (\"establishmentId\"): " + kind.getLabel().toLowerCase(java.util.Locale.ROOT)
                        + " do dono é sobre um lugar dele.");
            } else if (place != null && !Objects.equals(place.getOwnerId(), author.getId())) {
                throw new CommonExceptions.Forbidden(kind.getLabel() + " de estabelecimento só sobre um lugar seu.");
            } else if (place != null && place.getStatus() != Establishment.Status.ACTIVE) {
                errors.add("O lugar está fechado ou fora do ar: não dá para anunciar nele.");
            }
        }
        return place;
    }

    private Map<String, Object> background(Object raw, PostKind kind, UserAccount author, List<String> errors) {
        try {
            return backgrounds.validate(raw, new BackgroundValidator.Context(kind.feed(),
                    ages.protectedMinor(author.getUserDateOfBirth())));
        } catch (CommonExceptions.Invalid e) {
            errors.addAll(e.errors());
            return null;
        }
    }

    private String claimPhoto(long authorId, String uploadKey) {
        try {
            return media.claimImage(authorId, uploadKey, ImageSanitizer.Fit.POST);
        } catch (MediaExceptions.Invalid e) {
            throw new CommonExceptions.Invalid(e.errors());
        }
    }

    /** O post atual como pedido, com as chaves do PATCH por cima; o resultado passa pelas mesmas regras da criação. */
    private PostDraft merge(Post post, Map<String, Object> patch) {
        Post.Recipe r = post.getRecipe();
        Post.FoodAlert a = post.getFoodAlert();
        PostDraft current = new PostDraft(post.getKind().name(), post.getTitle(), post.getBody(),
                post.getMediaObjectKey(), post.getAltText(), post.getEstablishmentId(), post.getCuisine(),
                post.getFoodFormat(), post.getDietaryStyles(), post.getAllergens(), post.getFoodWarnings(),
                post.getAgeRating().name(), post.isAdvertising(), post.getStartsAt(), post.getEndsAt(),
                post.getPriceCents(), post.getPriceFromCents(), post.getPriceToCents(), post.getDiscountPercent(),
                post.getFreeEntry(), r == null ? null : new RecipeDraft(r.ingredients(), r.steps(), r.servings(),
                r.prepMinutes()), a == null ? null : new AlertDraft(a.product(), a.brand(), a.lots(), a.reason(),
                a.sourceUrl()), post.getCity(), null);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> merged = new LinkedHashMap<>(json.convertValue(current, Map.class));
            merged.putAll(patch);
            merged.put("background", null);     // a moldura é tratada à parte (só valida quando muda)
            return json.convertValue(merged, PostDraft.class);
        } catch (RuntimeException e) {
            throw new CommonExceptions.Invalid(List.of("Algum campo veio no formato errado: datas em ISO-8601 "
                    + "(2026-11-01T18:00:00-03:00), preços em centavos inteiros e listas como [\"…\"]."));
        }
    }

    private static void apply(Post post, PostRules.Content c, Establishment place, String photo) {
        post.setTitle(c.title());
        post.setBody(c.body());
        post.setMediaType(photo == null ? MediaType.TEXT : MediaType.IMAGE);
        post.setMediaObjectKey(photo);
        post.setAltText(photo == null ? null : c.altText());
        post.setEstablishmentId(place == null ? null : place.getId());
        post.setCuisine(c.cuisine() == null ? null : c.cuisine().name());
        post.setFoodFormat(c.format() == null ? null : c.format().name());
        post.setDietaryStyles(c.dietaryStyles().stream().map(Enum::name).toList());
        Map<String, String> allergens = new LinkedHashMap<>();
        c.allergens().forEach((k, v) -> allergens.put(k.name(), v.name()));
        post.setAllergens(allergens);
        post.setFoodWarnings(c.warnings().stream().map(Enum::name).toList());
        post.setAgeRating(c.ageRating());
        post.setAdvertising(c.advertising());
        post.setStartsAt(c.startsAt());
        post.setEndsAt(c.endsAt());
        post.setExpiresAt(c.expiresAt());
        post.setPriceCents(c.priceCents());
        post.setPriceFromCents(c.priceFromCents());
        post.setPriceToCents(c.priceToCents());
        post.setDiscountPercent(c.discountPercent());
        post.setFreeEntry(c.free());
        post.setRecipe(c.recipe());
        post.setFoodAlert(c.alert());
        post.setCity(c.city());
    }
}
