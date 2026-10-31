package com.friendlyeats.domain.moderation;

/**
 * Coluna {@code moderation_state} de post e comentário (RF13). {@code null} = normal. Os três estados tiram o
 * conteúdo do ar para os outros e guardam o texto para o recurso e a preservação (MCI, art. 15).
 */
public enum ContentModeration {
    /** Retirada preventiva até a decisão (criança em risco). */
    PREVENTIVE,
    /** Ocultado por decisão. */
    HIDDEN,
    /** Removido por decisão. */
    REMOVED
}
