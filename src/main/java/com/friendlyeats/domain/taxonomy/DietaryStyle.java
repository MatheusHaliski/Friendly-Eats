package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.Optional;

/**
 * Estilo alimentar: descreve a oferta do estabelecimento ou do prato, nunca a pessoa (RF7). Ligado a uma pessoa,
 * alergia, doença celíaca, halal ou kosher revelam saúde ou religião (LGPD Art. 11): no perfil só com o consentimento
 * {@code SENSITIVE_PROFILE_DATA}.
 */
public enum DietaryStyle {
    VEGANO("Vegano"),
    VEGETARIANO("Vegetariano"),
    SEM_GLUTEN("Sem glúten"),
    SEM_LACTOSE("Sem lactose"),
    LOW_CARB("Low carb"),
    HALAL("Halal"),
    KOSHER("Kosher"),
    ORGANICO("Orgânico"),
    PESCETARIANO("Pescetariano"),
    SEM_ACUCAR("Sem açúcar"),
    CRUDIVORO("Crudívoro");

    /** Quanto o estabelecimento atende ao estilo: tem opções ou é só isso. */
    public enum Level { OFERECE, EXCLUSIVO }

    private final String label;

    DietaryStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public static Optional<DietaryStyle> fromCode(String code) {
        return Arrays.stream(values()).filter(s -> s.name().equals(code)).findFirst();
    }
}
