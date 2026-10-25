package com.friendlyeats.domain.model;

/** Reações a um post (tabela {@code post_reaction}, RF12). */
public enum ReactionKind {
    /** Curtida: pública e contada em {@code post.like_count}. */
    LIKE,
    /** Salvar: privado; só quem salvou sabe, e não há contador público. */
    SAVE
}
