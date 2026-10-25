package com.friendlyeats.application.post;

import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.AgeRating;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.ShareChannel;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.taxonomy.Allergen;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Vocabulários do post para o formulário de publicação ({@code GET /api/v1/posts/vocabulary}, público, RF9): tipos com
 * quem publica e os campos de cada um, alergênicos, advertências, níveis, classificação e canais de compartilhamento.
 * Com sessão, {@code publishableKinds} traz os tipos que a conta pode publicar (sem conferir o lugar).
 */
@Component
public class PostVocabulary {

    public record KindItem(String code, String label, String lucideIcon, String border, String feed,
                           String whoCanPublish, List<String> requiredFields, List<String> optionalFields,
                           boolean photoRequired, String expires) {
    }

    public record AllergenItem(String code, String label, String kind, String status) {
    }

    public record Vocabulary(List<KindItem> kinds, List<String> publishableKinds, List<AllergenItem> allergens,
                             List<AllergenItem> warnings, List<Coded> allergenLevels, List<Coded> ageRatings,
                             List<Coded> shareChannels, String allergenNotice) {
    }

    private static final List<String> COMMON = List.of("body", "mediaObjectKey", "altText", "establishmentId",
            "cuisine", "foodFormat", "dietaryStyles", "allergens", "warnings", "ageRating", "advertising", "city",
            "background");

    private final UserAccountRepository users;

    public PostVocabulary(UserAccountRepository users) {
        this.users = users;
    }

    public Vocabulary get(Optional<Long> viewerId) {
        Optional<UserAccount> viewer = viewerId.flatMap(users::findById)
                .filter(u -> u.getAccountStatus() == AccountStatus.ACTIVE);
        List<String> publishable = viewer.map(u -> Arrays.stream(PostKind.values())
                .filter(k -> k.denialFor(u.getUserType(), u.getCategoryVerification()) == null)
                .map(Enum::name).toList()).orElse(List.of());
        List<AllergenItem> all = Arrays.stream(Allergen.values())
                .map(a -> new AllergenItem(a.name(), a.getLabel(), a.getKind().name(), a.getStatus().name())).toList();
        return new Vocabulary(Arrays.stream(PostKind.values()).map(PostVocabulary::item).toList(), publishable,
                all.stream().filter(a -> a.kind().equals("ALERGENICO")).toList(),
                all.stream().filter(a -> a.kind().equals("ADVERTENCIA")).toList(),
                Arrays.stream(Allergen.Level.values()).map(l -> new Coded(l.name(), l.getLabel(), null)).toList(),
                Arrays.stream(AgeRating.values()).map(r -> new Coded(r.name(), r.getLabel(), null)).toList(),
                Arrays.stream(ShareChannel.values()).map(c -> new Coded(c.name(), c.getLabel(), null)).toList(),
                Allergen.NOTICE);
    }

    private static KindItem item(PostKind k) {
        List<String> required = switch (k) {
            case FOTO_PRATO -> List.of("title", "mediaObjectKey", "altText");
            case RECEITA -> List.of("title", "recipe.ingredients", "recipe.steps");
            case PERGUNTA -> List.of("title");
            case PRATO_DO_DIA -> List.of("title", "establishmentId", "priceCents");
            case PROMOCAO -> List.of("title", "startsAt", "endsAt", "priceFromCents+priceToCents | discountPercent",
                    "establishmentId (dono) | advertising: true (criador)");
            case EVENTO -> List.of("title", "startsAt", "endsAt", "priceCents | free: true");
            case ALERTA_SANITARIO -> List.of("title", "alert.product", "alert.brand", "alert.lots", "alert.reason",
                    "alert.sourceUrl (https)");
        };
        List<String> extra = switch (k) {
            case FOTO_PRATO -> List.of("priceCents");
            case RECEITA -> List.of("recipe.servings", "recipe.prepMinutes");
            default -> List.of();
        };
        List<String> optional = new java.util.ArrayList<>(COMMON.stream()
                .filter(f -> required.stream().noneMatch(r -> r.startsWith(f)))
                .filter(f -> !(k == PostKind.ALERTA_SANITARIO && (f.equals("background") || f.equals("advertising")
                        || f.equals("ageRating"))))
                .toList());
        optional.addAll(extra);
        String expires = switch (k) {
            case PRATO_DO_DIA -> "Sai do feed às 23h59 (horário de Brasília) do dia da publicação.";
            case PROMOCAO, EVENTO -> "Sai do feed depois de \"endsAt\".";
            default -> null;
        };
        return new KindItem(k.name(), k.getLabel(), k.getLucideIcon(), k.getBorder(), k.feed().name(),
                k.getWhoCanPublish(), required, List.copyOf(optional), k == PostKind.FOTO_PRATO, expires);
    }
}
