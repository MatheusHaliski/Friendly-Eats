package com.friendlyeats.domain.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Ciclo de vida do post (docs/RF9/estados.md). "Em alta" não é um status gravado: sai do ranking no Valkey (RF14).
 * "Encerrado" (prato do dia, promoção ou evento vencido) também não: deriva de {@code expires_at}.
 */
public enum PostStatus {

    DRAFT,
    PUBLISHED,
    ARCHIVED,
    /** Terminal: apagado pelo autor, pela moderação ou pela eliminação da conta (LGPD). O conteúdo some. */
    REMOVED;

    /** Status que aparecem no perfil do autor (RF15) e abrem na página do post (RF14). */
    public static final Set<PostStatus> PUBLIC = EnumSet.of(PUBLISHED, ARCHIVED);

    public Set<PostStatus> allowedTransitions() {
        return switch (this) {
            case DRAFT -> EnumSet.of(PUBLISHED, REMOVED);
            case PUBLISHED -> EnumSet.of(ARCHIVED, REMOVED);
            case ARCHIVED -> EnumSet.of(PUBLISHED, REMOVED);
            case REMOVED -> EnumSet.noneOf(PostStatus.class);
        };
    }

    public boolean canTransitionTo(PostStatus target) {
        return allowedTransitions().contains(target);
    }
}
