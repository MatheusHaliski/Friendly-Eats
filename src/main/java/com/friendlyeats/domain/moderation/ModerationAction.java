package com.friendlyeats.domain.moderation;

/** Decisão sobre um alvo denunciado (RF13). Toda ação que não seja {@code KEEP} exige o motivo da política. */
public enum ModerationAction {
    /** Manter: a denúncia é improcedente e o conteúdo fica (ou volta) no ar. */
    KEEP("Manter"),
    /** Ocultar: some para os outros, o texto fica guardado. */
    HIDE("Ocultar"),
    /** Remover: some para os outros (perfil: conta suspensa; lugar: página fora do ar). */
    REMOVE("Remover"),
    /** Avisar o autor: o conteúdo fica, o autor recebe o motivo. */
    WARN("Avisar o autor");

    private final String label;

    ModerationAction(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Tira o conteúdo do ar. */
    public boolean takesDown() {
        return this == HIDE || this == REMOVE;
    }
}
