package com.friendlyeats.domain.model;

/**
 * Classificação do post (chip de texto no card; a cor nunca é o único sinal). {@code AGE_18} = bebida alcoólica como
 * tema ou atração: não aparece para visitante sem login nem para menor de 18, a mesma regra da página do lugar (RF6).
 */
public enum AgeRating {

    LIVRE("Livre"),
    AGE_18("18+");

    private final String label;

    AgeRating(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
