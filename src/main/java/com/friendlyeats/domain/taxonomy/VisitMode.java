package com.friendlyeats.domain.taxonomy;

/** Como foi a experiência avaliada (RF8); decide quais dimensões o formulário mostra. */
public enum VisitMode {
    DINE_IN("No local"), TAKEOUT("Retirada"), DELIVERY("Entrega");

    private final String label;

    VisitMode(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
