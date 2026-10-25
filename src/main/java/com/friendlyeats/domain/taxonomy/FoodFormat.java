package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.Optional;

/**
 * Tipo de comida / formato: o que se come ou compra, independentemente da origem (RF7). Pizza napolitana, paulistana
 * ou vegana continua sendo PIZZA; a tradição fica na {@link Cuisine}. A culinária sugerida é só um padrão editável na
 * tela, nunca aplicada automaticamente.
 */
public enum FoodFormat {
    PRATO_FEITO("Prato feito / marmita / executivo", FoodFamily.REFEICOES, Cuisine.BRASILEIRA, false),
    MASSAS("Massas", FoodFamily.REFEICOES, Cuisine.ITALIANA, false),
    SALADAS_BOWLS("Saladas e bowls", FoodFamily.REFEICOES, null, false),
    SOPAS_CALDOS("Sopas e caldos", FoodFamily.REFEICOES, null, false),
    CHURRASCO("Churrasco e carnes na brasa", FoodFamily.CARNES_PESCADOS, Cuisine.GAUCHA, false),
    ESPETINHO("Espetinho / churrasquinho", FoodFamily.CARNES_PESCADOS, Cuisine.BRASILEIRA, false),
    FRANGO("Frango", FoodFamily.CARNES_PESCADOS, null, false),
    FRUTOS_DO_MAR("Peixes e frutos do mar", FoodFamily.CARNES_PESCADOS, null, false),
    PIZZA("Pizza", FoodFamily.LANCHES, Cuisine.ITALIANA, false),
    HAMBURGUER("Hambúrguer", FoodFamily.LANCHES, Cuisine.NORTE_AMERICANA, false),
    SANDUICHE("Sanduíches e lanches", FoodFamily.LANCHES, null, false),
    CACHORRO_QUENTE("Cachorro-quente", FoodFamily.LANCHES, null, false),
    TACOS_BURRITOS("Tacos, burritos e tortilhas", FoodFamily.LANCHES, Cuisine.MEXICANA, false),
    KEBAB_SHAWARMA("Kebab e shawarma", FoodFamily.LANCHES, Cuisine.ARABE, false),
    SALGADOS("Salgados", FoodFamily.SALGADOS_E_PETISCOS, Cuisine.BRASILEIRA, false),
    PASTEL("Pastel", FoodFamily.SALGADOS_E_PETISCOS, Cuisine.BRASILEIRA, false),
    ESFIHA("Esfiha", FoodFamily.SALGADOS_E_PETISCOS, Cuisine.ARABE, false),
    TAPIOCA("Tapioca", FoodFamily.SALGADOS_E_PETISCOS, Cuisine.NORDESTINA, false),
    PETISCOS_BOTECO("Petiscos e porções de boteco", FoodFamily.SALGADOS_E_PETISCOS, Cuisine.BRASILEIRA, false),
    SUSHI("Sushi, sashimi e temaki", FoodFamily.ORIENTAIS_E_ETNICOS, Cuisine.JAPONESA, false),
    POKE("Poke", FoodFamily.ORIENTAIS_E_ETNICOS, Cuisine.HAVAIANA, false),
    LAMEN_NOODLES("Lámen e macarrões orientais", FoodFamily.ORIENTAIS_E_ETNICOS, null, false),
    ACAI("Açaí", FoodFamily.DOCES_E_GELADOS, Cuisine.AMAZONICA, false),
    SORVETE("Sorvete, gelato e picolé", FoodFamily.DOCES_E_GELADOS, null, false),
    DOCES_CONFEITARIA("Doces, bolos e confeitaria", FoodFamily.DOCES_E_GELADOS, null, false),
    CREPE_PANQUECA("Crepes, panquecas e waffles", FoodFamily.DOCES_E_GELADOS, null, false),
    PAES("Pães e padaria", FoodFamily.PADARIA_E_CAFE, null, false),
    CAFE("Café", FoodFamily.PADARIA_E_CAFE, null, false),
    CHA("Chás e bubble tea", FoodFamily.BEBIDAS, null, false),
    SUCOS_SMOOTHIES("Sucos, vitaminas e smoothies", FoodFamily.BEBIDAS, null, false),
    CERVEJA("Cerveja e chope", FoodFamily.BEBIDAS, null, true),
    VINHO("Vinho", FoodFamily.BEBIDAS, null, true),
    COQUETEIS_DESTILADOS("Drinques e destilados", FoodFamily.BEBIDAS, null, true);

    private final String label;
    private final FoodFamily family;
    private final Cuisine suggestedCuisine;
    private final boolean alcoholic;

    FoodFormat(String label, FoodFamily family, Cuisine suggestedCuisine, boolean alcoholic) {
        this.label = label;
        this.family = family;
        this.suggestedCuisine = suggestedCuisine;
        this.alcoholic = alcoholic;
    }

    public String getLabel() {
        return label;
    }

    public FoodFamily getFamily() {
        return family;
    }

    public Optional<Cuisine> suggestedCuisine() {
        return Optional.ofNullable(suggestedCuisine);
    }

    /**
     * Bebida alcoólica. Hoje só informa o app (filtros e avisos); quem torna a página 18+ é o tipo principal do
     * estabelecimento ({@link EstablishmentType#isAlcoholFocused()}). Posts sobre esses formatos ficam 18+ no RF9.
     */
    public boolean isAlcoholic() {
        return alcoholic;
    }

    public static Optional<FoodFormat> fromCode(String code) {
        return Arrays.stream(values()).filter(f -> f.name().equals(code)).findFirst();
    }
}
