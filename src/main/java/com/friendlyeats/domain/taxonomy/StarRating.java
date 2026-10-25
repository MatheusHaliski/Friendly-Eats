package com.friendlyeats.domain.taxonomy;

/** Âncoras verbais da nota inteira (RF8). Não existe nota 0: "sem avaliação" é ausência de nota. */
public enum StarRating {
    STAR_1(1, "Péssimo"), STAR_2(2, "Ruim"), STAR_3(3, "Regular"), STAR_4(4, "Bom"), STAR_5(5, "Excelente");

    private final int value;
    private final String label;

    StarRating(int value, String label) {
        this.value = value;
        this.label = label;
    }

    public int getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }

    public static StarRating of(int value) {
        if (value < 1 || value > 5) {
            throw new IllegalArgumentException("Nota fora de 1 a 5: " + value);
        }
        return values()[value - 1];
    }
}
