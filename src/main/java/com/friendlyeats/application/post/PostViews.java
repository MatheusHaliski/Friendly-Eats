package com.friendlyeats.application.post;

import com.friendlyeats.application.background.BackgroundService.BackgroundView;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.profile.AccountBadge;
import com.friendlyeats.domain.model.AgeRating;
import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.MediaType;
import com.friendlyeats.domain.model.PostStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Respostas da API de posts (RF14, usadas também pelo RF9, RF12 e RF15). Datas sempre em ISO-8601 UTC: o app mostra o
 * tempo relativo no card ("3 min") e a data absoluta no fuso de quem vê na página do post. Todo código vem com o
 * rótulo pt-BR, e toda categoria com cor, glifo ou ícone e rótulo.
 */
public final class PostViews {

    private PostViews() {
    }

    static String postPath(UUID id) {
        return "/api/v1/posts/" + id;
    }

    static String profilePath(Long userId) {
        return "/api/v1/users/" + userId + "/profile";
    }

    static String establishmentPath(long id) {
        return "/api/v1/establishments/" + id;
    }

    /**
     * Dono do post ou do comentário. {@code profilePath} é o destino do toque na foto ou no nome (RF15); fica
     * {@code null} quando a conta foi eliminada (nome "Conta excluída", sem bolinha) ou não está ativa.
     */
    public record AuthorView(Long id, String name, String handle, AccountBadge category, boolean verified,
                             String avatarUrl, String profilePath, boolean available) {
    }

    /** Tipo do post: rótulo, ícone Lucide e a forma da borda do card ({@code SEM_MOLDURA}, {@code TRACEJADA}…). */
    public record KindView(String code, String label, String lucideIcon, String border) {
    }

    /** Lugar citado no post; só vem quando a página dele está no ar para quem vê (senão, {@code null}). */
    public record PlaceRefView(long id, String name, String city, String uf, boolean adultsOnly, String pagePath) {
    }

    /** Alergênico declarado no prato: {@code level} = {@code CONTEM} ou {@code PODE_CONTER}. */
    public record AllergenView(String code, String label, String level, String levelLabel) {
    }

    /**
     * Preço em centavos e o texto pronto ({@code label}): "R$ 25,00", "De R$ 50,00 por R$ 35,00", "20% de desconto"
     * ou "Gratuito".
     */
    public record PriceView(Integer priceCents, Integer priceFromCents, Integer priceToCents, Integer discountPercent,
                            boolean free, String label) {
    }

    public record RecipeView(List<String> ingredients, List<String> steps, Integer servings, Integer prepMinutes) {
    }

    /** Alerta sanitário; {@code sourceHost} vai no rótulo "Fonte: …" ao lado do link. */
    public record AlertView(String product, String brand, List<String> lots, String reason, String sourceUrl,
                            String sourceHost) {
    }

    public record CountersView(long likes, long comments, long shares) {
    }

    /** Estado dos botões para quem está vendo; tudo {@code false} para o visitante. */
    public record ViewerStateView(boolean liked, boolean saved, boolean isAuthor) {
        static final ViewerStateView ANONYMOUS = new ViewerStateView(false, false, false);
    }

    /**
     * O que o post aceita (RF12). {@code open} = publicado (arquivado só se lê). Alerta sanitário aceita só
     * compartilhar e salvar. {@code commentAudience} é o {@code who_can_comment} do autor; {@code commentNotice} diz
     * por que quem vê não pode comentar (ou {@code null}).
     */
    public record InteractionsView(boolean open, boolean likes, boolean comments, boolean shares, boolean saves,
                                   String commentAudience, boolean viewerCanComment, String commentNotice) {
    }

    /**
     * Página do post (RF14). {@code allergenNotice} acompanha alergênicos, advertências e "sem glúten/lactose";
     * {@code expired} = prato do dia, promoção ou evento encerrado (fora do feed, rótulo "Encerrado");
     * {@code background} = moldura resolvida (RF16) ou {@code null}.
     */
    public record PostDetailView(UUID id, KindView kind, FeedType feed, MediaType mediaType, String title, String body,
                                 String mediaUrl, String altText, PlaceRefView establishment, Coded cuisine,
                                 Coded foodFormat, List<Coded> dietaryStyles, List<AllergenView> allergens,
                                 List<Coded> warnings, String allergenNotice, AgeRating ageRating,
                                 String ageRatingLabel, boolean advertising, String advertisingLabel,
                                 Instant startsAt, Instant endsAt, PriceView price, RecipeView recipe, AlertView alert,
                                 String city, Instant expiresAt, boolean expired, PostStatus status,
                                 Instant publishedAt, Instant updatedAt, AuthorView author, CountersView counts,
                                 ViewerStateView viewer, InteractionsView interactions, String shareUrl,
                                 BackgroundView background) {
    }

    /** Card do feed: sem o texto corrido ({@code excerpt} = até 160 caracteres) e sem a receita; o resto igual. */
    public record PostCardView(UUID id, KindView kind, FeedType feed, MediaType mediaType, String title,
                               String excerpt, String mediaUrl, String altText, PlaceRefView establishment,
                               Coded cuisine, Coded foodFormat, List<Coded> dietaryStyles,
                               List<AllergenView> allergens, List<Coded> warnings, AgeRating ageRating,
                               String ageRatingLabel, boolean advertising, String advertisingLabel, Instant startsAt,
                               Instant endsAt, PriceView price, AlertView alert, Instant expiresAt, boolean expired,
                               PostStatus status, Instant publishedAt, AuthorView author, CountersView counts,
                               ViewerStateView viewer, String detailPath, BackgroundView background) {
    }

    /** {@code nextCursor} vai no parâmetro {@code cursor} da próxima página; {@code null} = fim. */
    public record FeedPageView(List<PostCardView> items, String nextCursor) {
    }
}
