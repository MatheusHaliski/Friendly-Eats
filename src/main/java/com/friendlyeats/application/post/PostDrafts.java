package com.friendlyeats.application.post;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Pedidos de publicação e edição (RF9). Os códigos chegam como texto e são validados pelo {@link PostRules}, que
 * devolve todos os problemas juntos (422) com a mensagem em pt-BR.
 */
public final class PostDrafts {

    private PostDrafts() {
    }

    /**
     * Post como o app envia. {@code kind} e {@code ageRating} são códigos ({@code FOTO_PRATO}, {@code AGE_18}…);
     * {@code allergens} é {@code {"LEITE": "CONTEM"}}; {@code warnings} são as advertências ({@code LACTOSE},
     * {@code GLUTEN}). {@code background} é a moldura do RF16 (objeto JSON, opcional; {@code null} = padrão), validada
     * pelo {@code BackgroundValidator}; em alerta sanitário, qualquer moldura dá 422.
     */
    public record PostDraft(String kind, String title, String body, String mediaObjectKey, String altText,
                            Long establishmentId, String cuisine, String foodFormat, List<String> dietaryStyles,
                            Map<String, String> allergens, List<String> warnings, String ageRating,
                            Boolean advertising, Instant startsAt, Instant endsAt, Integer priceCents,
                            Integer priceFromCents, Integer priceToCents, Integer discountPercent, Boolean free,
                            RecipeDraft recipe, AlertDraft alert, String city, Object background) {
    }

    public record RecipeDraft(List<String> ingredients, List<String> steps, Integer servings, Integer prepMinutes) {
    }

    public record AlertDraft(String product, String brand, List<String> lots, String reason, String sourceUrl) {
    }
}
