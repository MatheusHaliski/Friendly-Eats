package com.friendlyeats.domain.taxonomy;

/** Macrorregião culinária (RF7). Cada uma tem uma culinária genérica, usada só quando não há uma específica. */
public enum CuisineMacro {
    BRASIL("Brasil"),
    AMERICA_LATINA("América Latina e Caribe"),
    AMERICA_NORTE("América do Norte"),
    EUROPA("Europa"),
    ORIENTE_MEDIO_NORTE_AFRICA("Oriente Médio e Norte da África"),
    AFRICA("África Subsaariana"),
    ASIA("Ásia");

    private final String label;

    CuisineMacro(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
