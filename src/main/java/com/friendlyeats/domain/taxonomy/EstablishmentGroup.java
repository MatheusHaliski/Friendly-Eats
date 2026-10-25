package com.friendlyeats.domain.taxonomy;

/**
 * Grupo do tipo de estabelecimento (RF6). Deriva do tipo principal e define ícone, card e o que se avalia: no
 * VAREJO, produtos e atendimento; nos demais, pratos e bebidas. POLO é contêiner de outros estabelecimentos.
 */
public enum EstablishmentGroup {
    RESTAURANTE("Restaurantes"),
    RAPIDO("Lanche e comida rápida"),
    BAR("Bares e bebidas"),
    CAFE_DOCE("Cafés, padarias e doces"),
    PRODUCAO("Cozinhas, encomendas e produção"),
    VAREJO("Varejo de alimentos e bebidas"),
    POLO("Mercados, feiras e polos");

    private final String label;

    EstablishmentGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
