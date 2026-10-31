package com.friendlyeats.domain.moderation;

/** Estado de um caso da fila (RF13). */
public enum CaseStatus {
    /** Na fila, esperando a primeira decisão. */
    OPEN("Na fila"),
    /** Decidido; corre o prazo de recurso. */
    DECIDED("Decidido"),
    /** Recurso aberto, esperando outra pessoa moderadora. */
    IN_APPEAL("Em recurso"),
    /** Recurso decidido: fim do caso. */
    FINAL("Encerrado");

    private final String label;

    CaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
