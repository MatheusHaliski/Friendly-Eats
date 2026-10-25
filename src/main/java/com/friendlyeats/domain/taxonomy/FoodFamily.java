package com.friendlyeats.domain.taxonomy;

/** Família de formatos: só agrupa filtros na tela; não é valor selecionável (RF7). */
public enum FoodFamily {
    REFEICOES("Refeições"),
    CARNES_PESCADOS("Carnes e pescados"),
    LANCHES("Lanches"),
    SALGADOS_E_PETISCOS("Salgados e petiscos"),
    ORIENTAIS_E_ETNICOS("Orientais e étnicos"),
    DOCES_E_GELADOS("Doces e gelados"),
    PADARIA_E_CAFE("Padaria e café"),
    BEBIDAS("Bebidas");

    private final String label;

    FoodFamily(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
