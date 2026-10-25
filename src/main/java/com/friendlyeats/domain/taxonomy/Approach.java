package com.friendlyeats.domain.taxonomy;

/**
 * Abordagem: o "jeito" da cozinha (RF7). Fusion, contemporânea e variada não são culinárias; combinam com elas.
 */
public enum Approach {
    TRADICIONAL("Tradicional"),
    CASEIRA("Comida caseira"),
    /** Exige culinária principal: BRASILEIRA + CONTEMPORANEA = "brasileira contemporânea". */
    CONTEMPORANEA("Contemporânea / autoral"),
    /** Exige 2+ culinárias ou uma principal genérica de macro (ASIATICA + FUSION = asian fusion). */
    FUSION("Fusion"),
    /** Sem tradição dominante (quilo, hotel): a culinária pode ficar vazia. */
    VARIADA("Variada / internacional");

    private final String label;

    Approach(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
