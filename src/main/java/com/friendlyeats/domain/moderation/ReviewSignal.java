package com.friendlyeats.domain.moderation;

/**
 * Sinais de fraude de uma avaliação (RF13 · RC-SEG-04). O autor vê o critério geral, nunca os limites, para a
 * explicação não ensinar o fraudador.
 */
public enum ReviewSignal {
    BURST_NEW_ACCOUNTS("Muitas avaliações de contas novas para o mesmo lugar em pouco tempo", true),
    SAME_ORIGIN("Avaliações de contas diferentes vindas da mesma origem", true),
    INCENTIVE_DECLARED("Quem avaliou declarou cortesia ou convite", false),
    CONFLICT_DECLARED("Quem avaliou declarou vínculo com o lugar", false);

    private final String label;
    private final boolean suspicious;

    ReviewSignal(String label, boolean suspicious) {
        this.label = label;
        this.suspicious = suspicious;
    }

    public String getLabel() {
        return label;
    }

    /** Põe a avaliação em análise (fora do agregado) até a decisão humana. */
    public boolean suspicious() {
        return suspicious;
    }
}
