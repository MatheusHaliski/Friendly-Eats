package com.friendlyeats.application.establishment;

import com.friendlyeats.application.account.AccountViews.CategoryView;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.taxonomy.CuisineChip;
import com.friendlyeats.domain.taxonomy.PriceLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Respostas da API de estabelecimentos (RF6, RF7) e avaliações (RF8). Todo código vem com o rótulo pt-BR. */
public final class EstablishmentViews {

    private EstablishmentViews() {
    }

    /** Código estável + rótulo; {@code group} é a macro da culinária, a família do formato ou o grupo do tipo. */
    public record Coded(String code, String label, String group) {
    }

    public record ChipView(String code, String label, String lucideIcon, String darkHex, String lightHex) {
        static ChipView of(CuisineChip c) {
            return c == null ? null : new ChipView(c.name(), c.getLabel(), c.getLucideIcon(), c.getDarkHex(), c.getLightHex());
        }
    }

    public record DietaryOfferView(String code, String label, String level) {
    }

    public record ClassificationView(Coded primaryCuisine, List<Coded> secondaryCuisines, Coded primaryFormat,
                                     List<Coded> secondaryFormats, Coded approach, List<DietaryOfferView> dietaryOffers,
                                     ChipView chip) {
    }

    /**
     * Faixa exibida: com 5+ relatos de gasto nos últimos 12 meses vale a mediana deles; senão, o declarado pelo dono.
     * O texto acessível evita que o leitor de tela diga "dólar dólar".
     */
    public record PriceView(PriceLevel level, String glyph, String label, String rangeLabel, PriceLevel.Source source,
                            int reportCount, Integer declaredMinCents, Integer declaredMaxCents, String accessibleLabel) {
    }

    /** Endereço como o público pode ver; o dono vê tudo. */
    public record LocationView(String country, String uf, String municipalityIbge, String city, String neighborhood,
                               String street, String streetNumber, String complement, String cep, Double latitude,
                               Double longitude, String geoPrecision, String addressVisibility, boolean approximate) {
    }

    public record RatingView(String state, BigDecimal average, String averageLabel, int count,
                             Map<Integer, Integer> histogram, Map<String, BigDecimal> dimensions,
                             String accessibleLabel) {
    }

    public record OwnerView(long id, String name, String handle, CategoryView category, boolean verified) {
    }

    /**
     * Página do estabelecimento. {@code published} e {@code publishBlockers} só vêm para o dono: a página fica
     * fora do ar enquanto o selo da conta não for conferido (RF1) ou a conta não estiver ativa.
     */
    public record EstablishmentView(long id, String name, String description, Coded primaryType,
                                    List<Coded> secondaryTypes, Coded group, ClassificationView classification,
                                    PriceView price, LocationView location, RatingView rating, OwnerView owner,
                                    boolean adultsOnly, Establishment.Status status, Boolean published,
                                    List<String> publishBlockers, Instant createdAt, Instant updatedAt) {
    }

    public record ReviewAuthorView(String name, String handle, CategoryView category) {
    }

    /** {@code status} (RF13): {@code PUBLISHED}; {@code UNDER_REVIEW} e {@code REMOVED} só aparecem para o autor. */
    public record ReviewView(long id, int rating, String ratingLabel, Coded visitMode, Map<String, Integer> dimensions,
                             Integer spentPerPersonCents, String body, String trust, boolean edited,
                             ReviewAuthorView author, Instant createdAt, Instant updatedAt, String status) {
    }

    public record ReviewPage(List<ReviewView> items, int page, int size, long total, RatingView rating) {
    }
}
