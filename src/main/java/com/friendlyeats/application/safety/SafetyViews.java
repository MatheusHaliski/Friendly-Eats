package com.friendlyeats.application.safety;

import com.friendlyeats.application.post.PostViews.AuthorView;

import java.time.Instant;
import java.util.List;

/** Respostas de Configurações → Privacidade → "Bloqueados, silenciados e palavras" (RF19). */
public final class SafetyViews {

    private SafetyViews() {
    }

    public record RelatedUserView(AuthorView user, Instant since) {
    }

    /** {@code locked}: menor de 16, o filtro padrão não desliga sem o responsável (ECA Digital). */
    public record DefaultFilterView(boolean enabled, boolean locked, int size) {
    }

    public record SafetyView(List<RelatedUserView> blocked, List<RelatedUserView> muted, List<String> words,
                             DefaultFilterView defaultFilter, int maxWords) {
    }

    /** A relação de quem vê com um perfil (botões do perfil público). */
    public record RelationView(long userId, boolean blocked, boolean muted) {
    }

    public record WordsRequest(List<String> words) {
    }

    public record SafetyPatch(Boolean defaultFilter) {
    }
}
