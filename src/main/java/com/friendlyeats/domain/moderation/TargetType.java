package com.friendlyeats.domain.moderation;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * O que pode ser denunciado (RF13) e o que a moderação pode fazer com cada um. Perfil e página de lugar não são
 * "ocultados": a retirada é a suspensão da conta ou a página fora do ar.
 */
public enum TargetType {
    POST("Post", EnumSet.allOf(ModerationAction.class)),
    COMMENT("Comentário", EnumSet.allOf(ModerationAction.class)),
    REVIEW("Avaliação", EnumSet.of(ModerationAction.KEEP, ModerationAction.REMOVE, ModerationAction.WARN)),
    PROFILE("Perfil", EnumSet.of(ModerationAction.KEEP, ModerationAction.REMOVE, ModerationAction.WARN)),
    ESTABLISHMENT("Página de lugar", EnumSet.of(ModerationAction.KEEP, ModerationAction.REMOVE, ModerationAction.WARN));

    private final String label;
    private final Set<ModerationAction> actions;

    TargetType(String label, Set<ModerationAction> actions) {
        this.label = label;
        this.actions = actions;
    }

    public String getLabel() {
        return label;
    }

    /** Ações que a moderação pode aplicar a este tipo de alvo. */
    public Set<ModerationAction> actions() {
        return actions;
    }

    /** Conteúdo que some na hora com a denúncia de criança em risco (perfil e lugar vão para o topo da fila). */
    public boolean preventiveHide() {
        return this == POST || this == COMMENT || this == REVIEW;
    }

    public static Optional<TargetType> fromCode(String code) {
        if (code == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(code.strip().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
