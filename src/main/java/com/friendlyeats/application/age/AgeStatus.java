package com.friendlyeats.application.age;

/** Estado da verificação de idade (RF17): começa {@code UNVERIFIED} e volta a ele quando a validade vence. */
public enum AgeStatus {
    UNVERIFIED("Idade não verificada"),
    PENDING("Verificação em andamento"),
    VERIFIED_ADULT("Maior de 18 verificado"),
    VERIFIED_MINOR("Menor de 18 verificado"),
    FAILED("Não foi possível verificar");

    private final String label;

    AgeStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
