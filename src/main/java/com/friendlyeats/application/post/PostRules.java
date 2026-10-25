package com.friendlyeats.application.post;

import com.friendlyeats.application.post.PostDrafts.AlertDraft;
import com.friendlyeats.application.post.PostDrafts.PostDraft;
import com.friendlyeats.application.post.PostDrafts.RecipeDraft;
import com.friendlyeats.domain.model.AgeRating;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.Allergen;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.FoodFormat;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Regras do post de comida (RF9), puras: cada método acrescenta os problemas em {@code errors}, para a API devolver
 * todos juntos (422), e devolve o valor normalizado. Quem pode publicar cada tipo está em {@link PostKind#denialFor}
 * (403); a posse do lugar é conferida no {@link PostPublishingService}, que carrega o estabelecimento, e a moldura
 * ({@code background}, RF16) pelo {@code BackgroundValidator}.
 */
public final class PostRules {

    public static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    /** O prato do dia sai do feed às 23h59 do dia da publicação, no horário de Brasília. */
    static final LocalTime DAILY_DISH_END = LocalTime.of(23, 59, 59);
    static final int MAX_TITLE = 140;
    static final int MAX_BODY = 5000;
    static final int MAX_ALT = 500;
    static final int MAX_CITY = 120;
    static final int MAX_DIETARY = 8;
    static final int MAX_INGREDIENTS = 60;
    static final int MAX_STEPS = 40;
    static final int MAX_LOTS = 50;
    /** R$ 50.000,00: teto de qualquer preço em centavos (um cupom de atacado cabe). */
    static final int MAX_PRICE_CENTS = 5_000_000;
    static final Duration MAX_HORIZON = Duration.ofDays(366);

    private PostRules() {
    }

    /**
     * O que as regras precisam saber além do pedido: quem publica, se tem 18 anos, se o lugar escolhido tem foco em
     * bebida alcoólica, se o post terá foto, quando foi publicado ({@code null} na criação) e o fim da vigência já
     * gravado (só um {@code endsAt} novo precisa estar no futuro).
     */
    public record Context(UserCategoryEnum authorType, boolean authorAdult, boolean placeAlcoholFocused,
                          boolean hasPhoto, Instant now, Instant publishedAt, Instant previousEndsAt) {
    }

    /** Conteúdo validado e normalizado, pronto para gravar. */
    public record Content(String title, String body, String altText, Cuisine cuisine, FoodFormat format,
                          List<DietaryStyle> dietaryStyles, Map<Allergen, Allergen.Level> allergens,
                          List<Allergen> warnings, AgeRating ageRating, boolean advertising, Instant startsAt,
                          Instant endsAt, Instant expiresAt, Integer priceCents, Integer priceFromCents,
                          Integer priceToCents, Integer discountPercent, Boolean free, Post.Recipe recipe,
                          Post.FoodAlert alert, String city) {
    }

    // ------------------------------------------------------------ tipo

    public static PostKind kind(String code, List<String> errors) {
        if (code == null || code.isBlank()) {
            errors.add("Escolha o tipo do post: " + codes(PostKind.values()) + ".");
            return null;
        }
        Optional<PostKind> kind = PostKind.fromCode(code.strip());
        if (kind.isEmpty()) {
            errors.add("Tipo de post desconhecido: " + code + ". Use " + codes(PostKind.values()) + ".");
        }
        return kind.orElse(null);
    }

    // ------------------------------------------------------------ conteúdo

    public static Content validate(PostKind kind, PostDraft d, Context c, List<String> errors) {
        String title = text(d.title(), "title", MAX_TITLE, errors);
        if (title == null) {
            errors.add(kind == PostKind.PERGUNTA ? "Escreva a pergunta no título." : "Dê um título ao post.");
        }
        String body = text(d.body(), "body", MAX_BODY, errors);
        String alt = text(d.altText(), "altText", MAX_ALT, errors);
        String city = text(d.city(), "city", MAX_CITY, errors);
        if (c.hasPhoto() && alt == null) {
            errors.add("Descreva a foto em \"altText\" (texto alternativo para quem usa leitor de tela).");
        }
        if (!c.hasPhoto() && alt != null) {
            errors.add("\"altText\" descreve a foto: envie a foto ou tire o texto alternativo.");
        }
        if (kind == PostKind.FOTO_PRATO && !c.hasPhoto()) {
            errors.add("Foto de prato precisa da foto: envie antes (POST /api/v1/media/uploads) e informe "
                    + "\"mediaObjectKey\".");
        }

        Cuisine cuisine = parse(d.cuisine(), Cuisine::fromCode, "cuisine", errors);
        FoodFormat format = parse(d.foodFormat(), FoodFormat::fromCode, "foodFormat", errors);
        List<DietaryStyle> dietary = parseList(d.dietaryStyles(), DietaryStyle::fromCode, "dietaryStyles", errors);
        if (dietary.size() > MAX_DIETARY) {
            errors.add("No máximo " + MAX_DIETARY + " estilos alimentares.");
        }
        Map<Allergen, Allergen.Level> allergens = allergens(d.allergens(), errors);
        List<Allergen> warnings = warnings(d.warnings(), errors);

        AgeRating requested = parse(d.ageRating(), code -> Arrays.stream(AgeRating.values())
                .filter(r -> r.name().equals(code)).findFirst(), "ageRating", errors);
        if (kind == PostKind.ALERTA_SANITARIO && requested == AgeRating.AGE_18) {
            errors.add("Alerta sanitário é sempre Livre: ele precisa chegar a todo mundo.");
        }
        AgeRating rating = ageRating(kind, requested, format, c.placeAlcoholFocused());
        if (rating == AgeRating.AGE_18 && !c.authorAdult()) {
            errors.add("Menores de 18 anos não publicam conteúdo de bebida alcoólica (18+).");
        }

        boolean advertising = Boolean.TRUE.equals(d.advertising());
        if (advertising && kind == PostKind.ALERTA_SANITARIO) {
            errors.add("Alerta sanitário não é publicidade: tire o chip Publicidade.");
        }
        if (kind == PostKind.PROMOCAO && c.authorType() == UserCategoryEnum.CRIADOR && !advertising) {
            errors.add("Promoção publicada por criador leva o chip Publicidade (\"advertising\": true), "
                    + "como pede o CDC (art. 36).");
        }

        boolean dated = kind == PostKind.PROMOCAO || kind == PostKind.EVENTO;
        notApplicable(kind, !dated, d.startsAt(), "startsAt", errors);
        notApplicable(kind, !dated, d.endsAt(), "endsAt", errors);
        if (dated) {
            period(kind, d.startsAt(), d.endsAt(), c, errors);
        }

        // preço em centavos: o de/por e o desconto são da promoção; o preço simples, do prato e do evento
        boolean promo = kind == PostKind.PROMOCAO;
        notApplicable(kind, !promo, d.priceFromCents(), "priceFromCents", errors);
        notApplicable(kind, !promo, d.priceToCents(), "priceToCents", errors);
        notApplicable(kind, !promo, d.discountPercent(), "discountPercent", errors);
        boolean simplePrice = kind == PostKind.FOTO_PRATO || kind == PostKind.PRATO_DO_DIA || kind == PostKind.EVENTO;
        notApplicable(kind, !simplePrice, d.priceCents(), "priceCents", errors);
        notApplicable(kind, kind != PostKind.EVENTO, d.free(), "free", errors);
        if (promo) {
            promotionPrice(d, errors);
        }
        if (d.priceCents() != null && (d.priceCents() < 0 || d.priceCents() > MAX_PRICE_CENTS)) {
            errors.add("Preço em centavos entre 0 e " + MAX_PRICE_CENTS + " (R$ 50.000,00).");
        }
        if (kind == PostKind.PRATO_DO_DIA && d.priceCents() == null) {
            errors.add("Informe o preço do prato do dia em centavos (\"priceCents\"): o preço exibido é o cobrado "
                    + "(CDC art. 31).");
        }
        Boolean free = d.free();
        if (kind == PostKind.EVENTO) {
            boolean isFree = Boolean.TRUE.equals(free);
            if (isFree == (d.priceCents() != null)) {
                errors.add("Evento: informe o preço em centavos (\"priceCents\") ou marque \"free\": true, um dos dois.");
            }
            free = isFree;
        }

        notApplicable(kind, kind != PostKind.RECEITA, d.recipe(), "recipe", errors);
        Post.Recipe recipe = kind == PostKind.RECEITA ? recipe(d.recipe(), errors) : null;
        notApplicable(kind, kind != PostKind.ALERTA_SANITARIO, d.alert(), "alert", errors);
        Post.FoodAlert alert = kind == PostKind.ALERTA_SANITARIO ? alert(d.alert(), errors) : null;

        Instant published = c.publishedAt() == null ? c.now() : c.publishedAt();
        return new Content(title, body, c.hasPhoto() ? alt : null, cuisine, format, dietary, allergens, warnings,
                rating, advertising, dated ? d.startsAt() : null, dated ? d.endsAt() : null,
                expiresAt(kind, published, d.endsAt()), d.priceCents(), d.priceFromCents(), d.priceToCents(),
                d.discountPercent(), free, recipe, alert, city);
    }

    /**
     * 18+ automático: formato alcoólico (cerveja, vinho, drinques) ou lugar com foco em bebida alcoólica deixam o post
     * 18+, peça o que pedir. O alerta sanitário é sempre Livre (um recall de cachaça precisa chegar a todos).
     */
    public static AgeRating ageRating(PostKind kind, AgeRating requested, FoodFormat format, boolean placeAlcohol) {
        if (kind == PostKind.ALERTA_SANITARIO) {
            return AgeRating.LIVRE;
        }
        if ((format != null && format.isAlcoholic()) || placeAlcohol) {
            return AgeRating.AGE_18;
        }
        return requested == null ? AgeRating.LIVRE : requested;
    }

    /**
     * Quando o post sai do feed: o prato do dia às 23h59 (horário de Brasília) do dia da publicação; promoção e evento
     * no fim da vigência; os demais, nunca.
     */
    public static Instant expiresAt(PostKind kind, Instant publishedAt, Instant endsAt) {
        return switch (kind) {
            case PRATO_DO_DIA -> publishedAt.atZone(BRAZIL).toLocalDate().atTime(DAILY_DISH_END).atZone(BRAZIL)
                    .toInstant();
            case PROMOCAO, EVENTO -> endsAt;
            default -> null;
        };
    }

    // ------------------------------------------------------------ partes

    private static void period(PostKind kind, Instant starts, Instant ends, Context c, List<String> errors) {
        String what = kind == PostKind.PROMOCAO ? "a vigência da promoção" : "o horário do evento";
        if (starts == null || ends == null) {
            errors.add("Informe " + what + ": \"startsAt\" e \"endsAt\" (ISO-8601, ex.: 2026-11-01T18:00:00-03:00).");
            return;
        }
        if (!ends.isAfter(starts)) {
            errors.add("O fim (\"endsAt\") precisa ser depois do início (\"startsAt\").");
        }
        if (!Objects.equals(ends, c.previousEndsAt()) && !ends.isAfter(c.now())) {
            errors.add("O fim (\"endsAt\") já passou.");
        }
        if (ends.isAfter(c.now().plus(MAX_HORIZON))) {
            errors.add("O fim (\"endsAt\") vai até um ano a partir de hoje.");
        }
    }

    private static void promotionPrice(PostDraft d, List<String> errors) {
        boolean fromTo = d.priceFromCents() != null || d.priceToCents() != null;
        boolean discount = d.discountPercent() != null;
        if (fromTo == discount) {
            errors.add("Promoção: informe o \"de/por\" em centavos (\"priceFromCents\" e \"priceToCents\") ou o "
                    + "desconto (\"discountPercent\"), um dos dois.");
        }
        if (fromTo) {
            if (d.priceFromCents() == null || d.priceToCents() == null) {
                errors.add("Informe os dois preços do \"de/por\": \"priceFromCents\" e \"priceToCents\".");
            } else {
                if (d.priceToCents() < 1 || d.priceFromCents() > MAX_PRICE_CENTS) {
                    errors.add("Preços do \"de/por\" entre 1 centavo e R$ 50.000,00.");
                }
                if (d.priceFromCents() <= d.priceToCents()) {
                    errors.add("No \"de/por\", o preço \"de\" precisa ser maior que o \"por\".");
                }
            }
        }
        if (discount && (d.discountPercent() < 1 || d.discountPercent() > 90)) {
            errors.add("Desconto entre 1% e 90%.");
        }
    }

    private static Map<Allergen, Allergen.Level> allergens(Map<String, String> raw, List<String> errors) {
        Map<Allergen, Allergen.Level> out = new EnumMap<>(Allergen.class);
        if (raw == null) {
            return out;
        }
        raw.forEach((code, level) -> {
            Optional<Allergen> a = Allergen.fromCode(code);
            Optional<Allergen.Level> l = Arrays.stream(Allergen.Level.values())
                    .filter(x -> x.name().equals(level)).findFirst();
            if (a.isEmpty()) {
                errors.add("Alergênico desconhecido: \"" + code + "\" (veja GET /api/v1/posts/vocabulary).");
            } else if (a.get().isWarning()) {
                errors.add(a.get().getLabel() + " é advertência, não alergênico: informe em \"warnings\".");
            } else if (a.get().getStatus() != Allergen.Status.VIGENTE) {
                errors.add(a.get().getLabel() + " ainda não está na lista vigente da Anvisa (proposta de 2025).");
            } else if (l.isEmpty()) {
                errors.add("Nível de \"" + code + "\": use CONTEM ou PODE_CONTER.");
            } else {
                out.put(a.get(), l.get());
            }
        });
        return out;
    }

    private static List<Allergen> warnings(List<String> raw, List<String> errors) {
        List<Allergen> out = parseList(raw, Allergen::fromCode, "warnings", errors);
        if (out.stream().anyMatch(a -> !a.isWarning())) {
            errors.add("\"warnings\" aceita só as advertências LACTOSE e GLUTEN; alergênicos vão em \"allergens\".");
            return out.stream().filter(Allergen::isWarning).toList();
        }
        return out;
    }

    private static Post.Recipe recipe(RecipeDraft r, List<String> errors) {
        if (r == null) {
            errors.add("Receita precisa de \"recipe\" com os ingredientes e o modo de preparo.");
            return null;
        }
        List<String> ingredients = lines(r.ingredients(), "recipe.ingredients", MAX_INGREDIENTS, 300, errors);
        List<String> steps = lines(r.steps(), "recipe.steps", MAX_STEPS, 1000, errors);
        if (ingredients.isEmpty()) {
            errors.add("Liste ao menos um ingrediente (com a quantidade, ex.: \"2 xícaras de farinha\").");
        }
        if (steps.isEmpty()) {
            errors.add("Escreva ao menos um passo do modo de preparo.");
        }
        if (r.servings() != null && (r.servings() < 1 || r.servings() > 100)) {
            errors.add("Rendimento entre 1 e 100 porções.");
        }
        if (r.prepMinutes() != null && (r.prepMinutes() < 1 || r.prepMinutes() > 2880)) {
            errors.add("Tempo de preparo entre 1 minuto e 48 horas.");
        }
        return new Post.Recipe(ingredients, steps, r.servings(), r.prepMinutes());
    }

    private static Post.FoodAlert alert(AlertDraft a, List<String> errors) {
        if (a == null) {
            errors.add("Alerta sanitário precisa de \"alert\" com produto, marca, lotes, motivo e a fonte oficial.");
            return null;
        }
        String product = text(a.product(), "alert.product", 140, errors);
        String brand = text(a.brand(), "alert.brand", 120, errors);
        String reason = text(a.reason(), "alert.reason", 1000, errors);
        List<String> lots = lines(a.lots(), "alert.lots", MAX_LOTS, 60, errors);
        String source = text(a.sourceUrl(), "alert.sourceUrl", 500, errors);
        if (product == null) errors.add("Informe o produto do alerta.");
        if (brand == null) errors.add("Informe a marca do produto.");
        if (lots.isEmpty()) errors.add("Informe os lotes afetados (ou \"todos os lotes\").");
        if (reason == null) errors.add("Informe o motivo do alerta.");
        if (source == null) {
            errors.add("Informe a fonte oficial do alerta (\"sourceUrl\", https).");
        } else if (!httpsUrl(source)) {
            errors.add("A fonte do alerta precisa ser um endereço https, ex.: https://www.gov.br/anvisa/….");
        }
        return new Post.FoodAlert(product, brand, lots, reason, source);
    }

    static boolean httpsUrl(String s) {
        try {
            URI u = URI.create(s);
            return "https".equalsIgnoreCase(u.getScheme()) && u.getHost() != null && !u.getHost().isBlank()
                    && u.getUserInfo() == null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ apoio

    private static void notApplicable(PostKind kind, boolean forbidden, Object value, String field, List<String> errors) {
        if (forbidden && value != null) {
            errors.add("\"" + field + "\" não se aplica a " + kind.getLabel() + ".");
        }
    }

    static <T> T parse(String code, Function<String, Optional<T>> lookup, String field, List<String> errors) {
        if (code == null || code.isBlank()) {
            return null;
        }
        Optional<T> v = lookup.apply(code.strip());
        if (v.isEmpty()) {
            errors.add("Código desconhecido em \"" + field + "\": " + code + " (veja GET /api/v1/taxonomy).");
        }
        return v.orElse(null);
    }

    static <T> List<T> parseList(List<String> codes, Function<String, Optional<T>> lookup, String field,
                                 List<String> errors) {
        if (codes == null) {
            return List.of();
        }
        Set<T> out = new LinkedHashSet<>();
        for (String c : codes) {
            T v = parse(c, lookup, field, errors);
            if (v == null && (c == null || c.isBlank())) {
                errors.add("Código vazio em \"" + field + "\".");
            } else if (v != null && !out.add(v)) {
                errors.add("Código repetido em \"" + field + "\": " + c + ".");
            }
        }
        return List.copyOf(out);
    }

    private static List<String> lines(List<String> raw, String field, int max, int maxLength, List<String> errors) {
        if (raw == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String s : raw) {
            String t = s == null ? null : s.strip();
            if (t == null || t.isEmpty()) {
                continue;
            }
            if (t.length() > maxLength) {
                errors.add("Cada item de \"" + field + "\" vai até " + maxLength + " caracteres.");
                break;
            }
            out.add(t);
        }
        if (out.size() > max) {
            errors.add("No máximo " + max + " itens em \"" + field + "\".");
        }
        return List.copyOf(out);
    }

    static String text(String v, String field, int max, List<String> errors) {
        String s = v == null || v.isBlank() ? null : v.strip();
        if (s != null && s.length() > max) {
            errors.add("\"" + field + "\" passa de " + max + " caracteres.");
        }
        return s;
    }

    private static String codes(Enum<?>[] values) {
        return String.join(", ", Arrays.stream(values).map(Enum::name).toList());
    }
}
