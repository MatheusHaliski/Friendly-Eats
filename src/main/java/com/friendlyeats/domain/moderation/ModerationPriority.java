package com.friendlyeats.domain.moderation;

import java.time.Duration;

/**
 * Prioridade da fila (RF13) e o prazo de revisão humana de cada nível. Os prazos são metas da casa ⚖️ (o ECA
 * Digital pede retirada "imediata" para criança em risco, o que a retirada preventiva cobre).
 */
public enum ModerationPriority {
    CRITICA("Crítica", Duration.ofHours(24)),
    URGENTE("Urgente", Duration.ofHours(24)),
    ALTA("Alta", Duration.ofHours(72)),
    NORMAL("Normal", Duration.ofDays(7)),
    BAIXA("Baixa", Duration.ofDays(14));

    private final String label;
    private final Duration sla;

    ModerationPriority(String label, Duration sla) {
        this.label = label;
        this.sla = sla;
    }

    public String getLabel() {
        return label;
    }

    public Duration sla() {
        return sla;
    }

    public int level() {
        return ordinal();
    }

    public static ModerationPriority ofLevel(int level) {
        return values()[Math.clamp(level, 0, values().length - 1)];
    }

    /** Um nível abaixo (denunciante com muitas denúncias improcedentes); a crítica nunca desce. */
    public ModerationPriority lowered() {
        return this == CRITICA ? CRITICA : ofLevel(level() + 1);
    }
}
