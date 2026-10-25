package com.friendlyeats.domain.model;

/** Canais da folha de compartilhamento (RF12). {@code NATIVE} = Web Share API ou folha do sistema. */
public enum ShareChannel {
    COPY_LINK("Copiar link"),
    NATIVE("Compartilhar pelo sistema"),
    WHATSAPP("WhatsApp"),
    TELEGRAM("Telegram"),
    X("X"),
    EMAIL("E-mail");

    private final String label;

    ShareChannel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
