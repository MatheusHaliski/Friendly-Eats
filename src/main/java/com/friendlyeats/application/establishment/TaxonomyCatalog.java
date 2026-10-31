package com.friendlyeats.application.establishment;

import com.friendlyeats.application.establishment.EstablishmentViews.ChipView;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.domain.taxonomy.ActionProvider;
import com.friendlyeats.domain.taxonomy.Approach;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.CuisineChip;
import com.friendlyeats.domain.taxonomy.CuisineMacro;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.EstablishmentGroup;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.Facet;
import com.friendlyeats.domain.taxonomy.FoodFamily;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.PriceLevel;
import com.friendlyeats.domain.taxonomy.ReviewDimension;
import com.friendlyeats.domain.taxonomy.StarRating;
import com.friendlyeats.domain.taxonomy.VisitMode;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * Vocabulários da taxonomia para o app montar filtros e formulários (GET /api/v1/taxonomy, público). A fonte de
 * verdade é o código; docs/TAXONOMIA_FRIENDLY_EATS.md descreve as regras.
 */
@Component
public class TaxonomyCatalog {

    public record CuisineItem(String code, String label, String macro, String parent, String ibgeRegion) {
    }

    public record FormatItem(String code, String label, String family, String suggestedCuisine, boolean alcoholic) {
    }

    public record TypeItem(String code, String label, String group, boolean adultsOnly, List<String> typicalCnae) {
    }

    public record PriceItem(String code, String glyph, String label, String rangeLabel) {
    }

    public record DimensionItem(String code, String label, List<String> visitModes) {
    }

    public record StarItem(String code, int value, String label) {
    }

    /** Faceta da página do lugar (RF11): {@code values} com rótulo; {@code declarable} = o dono escolhe o valor. */
    public record FacetItem(String code, String label, String section, String sectionLabel, String lucideIcon,
                            List<Coded> values, boolean declarable, boolean fixed, List<String> groups) {
    }

    /** Provedor de botão de ação (RF11): {@code kind} RESERVA, DELIVERY ou WHATSAPP e os domínios aceitos. */
    public record ActionProviderItem(String code, String label, String kind, List<String> domains) {
    }

    public record Catalog(List<Coded> cuisineMacros, List<CuisineItem> cuisines, List<Coded> foodFamilies,
                          List<FormatItem> formats, List<Coded> approaches, List<Coded> dietaryStyles,
                          List<String> dietaryLevels, List<Coded> establishmentGroups, List<TypeItem> establishmentTypes,
                          List<PriceItem> priceLevels, List<StarItem> stars, List<Coded> visitModes,
                          List<DimensionItem> reviewDimensions, List<ChipView> cuisineChips,
                          List<FacetItem> facets, List<ActionProviderItem> actionProviders, List<Coded> couvertKinds) {
    }

    private final PriceScale prices;

    public TaxonomyCatalog(PriceScale prices) {
        this.prices = prices;
    }

    public Catalog catalog() {
        return new Catalog(
                Arrays.stream(CuisineMacro.values()).map(m -> new Coded(m.name(), m.getLabel(), null)).toList(),
                Arrays.stream(Cuisine.values()).map(c -> new CuisineItem(c.name(), c.getLabel(), c.getMacro().name(),
                        c.parent().map(Enum::name).orElse(null), c.getIbgeRegion())).toList(),
                Arrays.stream(FoodFamily.values()).map(f -> new Coded(f.name(), f.getLabel(), null)).toList(),
                Arrays.stream(FoodFormat.values()).map(f -> new FormatItem(f.name(), f.getLabel(), f.getFamily().name(),
                        f.suggestedCuisine().map(Enum::name).orElse(null), f.isAlcoholic())).toList(),
                Arrays.stream(Approach.values()).map(a -> new Coded(a.name(), a.getLabel(), null)).toList(),
                Arrays.stream(DietaryStyle.values()).map(s -> new Coded(s.name(), s.getLabel(), null)).toList(),
                Arrays.stream(DietaryStyle.Level.values()).map(Enum::name).toList(),
                Arrays.stream(EstablishmentGroup.values()).map(g -> new Coded(g.name(), g.getLabel(), null)).toList(),
                Arrays.stream(EstablishmentType.values()).map(t -> new TypeItem(t.name(), t.getLabel(),
                        t.getGroup().name(), t.isAlcoholFocused(), t.getTypicalCnae())).toList(),
                Arrays.stream(PriceLevel.values()).map(p -> new PriceItem(p.name(), p.getGlyph(), p.getLabel(),
                        prices.rangeLabel(p))).toList(),
                Arrays.stream(StarRating.values()).map(s -> new StarItem(s.name(), s.getValue(), s.getLabel())).toList(),
                Arrays.stream(VisitMode.values()).map(v -> new Coded(v.name(), v.getLabel(), null)).toList(),
                Arrays.stream(ReviewDimension.values()).map(d -> new DimensionItem(d.name(), d.getLabel(),
                        Arrays.stream(VisitMode.values()).filter(d::appliesTo).map(Enum::name).toList())).toList(),
                Arrays.stream(CuisineChip.values()).map(ChipView::of).toList(),
                Arrays.stream(Facet.values()).map(f -> new FacetItem(f.name(), f.getLabel(), f.getSection().name(),
                        f.getSection().getLabel(), f.getLucideIcon(),
                        f.getValues().getCodes().stream().map(v -> new Coded(v, f.valueLabel(v), null)).toList(),
                        f.declarable(), f.fixed(), f.getGroups().stream().map(Enum::name).sorted().toList())).toList(),
                Arrays.stream(ActionProvider.values()).map(p -> new ActionProviderItem(p.name(), p.getLabel(),
                        p.getKind().name(), p.getDomains())).toList(),
                List.of(new Coded("ARTISTICO", "Couvert artístico (apresentação ao vivo)", null),
                        new Coded("ENTRADA_MESA", "Couvert de entrada (pão, petiscos)", null)));
    }
}
