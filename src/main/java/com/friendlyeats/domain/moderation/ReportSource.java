package com.friendlyeats.domain.moderation;

/** De onde veio a denúncia (RF13). */
public enum ReportSource {
    /** Pessoa com conta, pelo botão "Denunciar". */
    USER("Pessoa usuária"),
    /** Formulário público, sem conta (STF, Temas 987 e 533, item 9: usuários e não usuários). */
    PUBLIC_FORM("Formulário público"),
    /** Contestação de avaliação pelo dono do lugar (RC-SEG-08). */
    OWNER_CONTEST("Contestação do dono"),
    /** Sinal automático de fraude em avaliação (RC-SEG-04). */
    SYSTEM("Sinal automático");

    private final String label;

    ReportSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
