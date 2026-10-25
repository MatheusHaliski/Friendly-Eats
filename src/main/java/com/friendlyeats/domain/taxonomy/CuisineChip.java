package com.friendlyeats.domain.taxonomy;

import java.util.Map;

/**
 * Chip-pílula de culinária no card e no pin do mapa (docs/DESIGN_SYSTEM.md): pictograma Lucide + rótulo + cor. A cor
 * nunca é o único sinal. Deriva da classificação: casa só vegana/vegetariana → VEGETAL; senão a macro da culinária
 * principal; senão a família do formato principal.
 */
public enum CuisineChip {
    BRASILEIRA("Brasileira", "cooking-pot", "#DDAE14", "#6B5300"),
    EUROPEIA("Europeia", "wine", "#8DBAFF", "#0B57B8"),
    ASIATICA("Asiática", "soup", "#FF8C7E", "#B3141B"),
    AMERICAS("Américas", "beef", "#12AAAA", "#017C7C"),
    ORIENTE_MEDIO_AFRICA("Oriente Médio & África", "bean", "#AC8CE6", "#5E3E92"),
    LANCHES_PIZZA("Lanches & pizza", "pizza", "#E07C0A", "#A65A06"),
    DOCES_CAFE("Doces & café", "coffee", "#FFC1DB", "#8E1A5D"),
    VEGETAL("Vegetal", "vegan", "#6FE699", "#08693A");

    private static final Map<CuisineMacro, CuisineChip> BY_MACRO = Map.of(
            CuisineMacro.BRASIL, BRASILEIRA,
            CuisineMacro.EUROPA, EUROPEIA,
            CuisineMacro.ASIA, ASIATICA,
            CuisineMacro.AMERICA_LATINA, AMERICAS,
            CuisineMacro.AMERICA_NORTE, AMERICAS,
            CuisineMacro.ORIENTE_MEDIO_NORTE_AFRICA, ORIENTE_MEDIO_AFRICA,
            CuisineMacro.AFRICA, ORIENTE_MEDIO_AFRICA);

    private static final Map<FoodFamily, CuisineChip> BY_FAMILY = Map.of(
            FoodFamily.LANCHES, LANCHES_PIZZA,
            FoodFamily.SALGADOS_E_PETISCOS, LANCHES_PIZZA,
            FoodFamily.DOCES_E_GELADOS, DOCES_CAFE,
            FoodFamily.PADARIA_E_CAFE, DOCES_CAFE,
            FoodFamily.BEBIDAS, DOCES_CAFE,
            FoodFamily.REFEICOES, BRASILEIRA,
            FoodFamily.CARNES_PESCADOS, BRASILEIRA,
            FoodFamily.ORIENTAIS_E_ETNICOS, ASIATICA);

    private final String label;
    private final String lucideIcon;
    private final String darkHex;
    private final String lightHex;

    CuisineChip(String label, String lucideIcon, String darkHex, String lightHex) {
        this.label = label;
        this.lucideIcon = lucideIcon;
        this.darkHex = darkHex;
        this.lightHex = lightHex;
    }

    /** Nulo quando não há culinária, formato nem estilo exclusivo (ex.: supermercado). */
    public static CuisineChip of(Cuisine primaryCuisine, FoodFormat primaryFormat,
                                 Map<DietaryStyle, DietaryStyle.Level> dietaryOffers) {
        if (dietaryOffers.get(DietaryStyle.VEGANO) == DietaryStyle.Level.EXCLUSIVO
                || dietaryOffers.get(DietaryStyle.VEGETARIANO) == DietaryStyle.Level.EXCLUSIVO) {
            return VEGETAL;
        }
        if (primaryCuisine != null) {
            return BY_MACRO.get(primaryCuisine.getMacro());
        }
        return primaryFormat == null ? null : BY_FAMILY.get(primaryFormat.getFamily());
    }

    public String getLabel() {
        return label;
    }

    public String getLucideIcon() {
        return lucideIcon;
    }

    public String getDarkHex() {
        return darkHex;
    }

    public String getLightHex() {
        return lightHex;
    }
}
