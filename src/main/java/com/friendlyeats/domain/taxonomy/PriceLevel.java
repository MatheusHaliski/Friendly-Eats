package com.friendlyeats.domain.taxonomy;

/**
 * Faixa de preço por pessoa (RF6): prato + bebida não alcoólica numa refeição principal. O valor em reais é o dado de
 * origem; o nível é derivado pela {@code PriceScale} vigente e muda sozinho quando a escala é reajustada.
 */
public enum PriceLevel {
    PRICE_UNKNOWN(null, "Preço não informado"),
    PRICE_1("$", "Econômico"),
    PRICE_2("$$", "Moderado"),
    PRICE_3("$$$", "Caro"),
    PRICE_4("$$$$", "Muito caro"),
    /** Varejo sem refeição (mercearia, empório): preço por pessoa não faz sentido. */
    PRICE_NA(null, "Não se aplica");

    /** De onde vem o nível exibido. */
    public enum Source { PRICE_SRC_DECLARED, PRICE_SRC_REVIEWS }

    private final String glyph;
    private final String label;

    PriceLevel(String glyph, String label) {
        this.glyph = glyph;
        this.label = label;
    }

    public String getGlyph() {
        return glyph;
    }

    public String getLabel() {
        return label;
    }
}
