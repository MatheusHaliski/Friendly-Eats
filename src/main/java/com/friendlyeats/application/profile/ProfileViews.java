package com.friendlyeats.application.profile;

import java.time.Instant;
import java.util.List;

/**
 * Respostas do perfil público e do feed de perfis (RF15). Minimização (LGPD Art. 6º, III): nunca saem e-mail,
 * telefone, data de nascimento, documentos nem a cidade de residência; a cidade mostrada é a de atuação declarada no
 * cadastro do tipo de conta, e só para quem não é comensal.
 */
public final class ProfileViews {

    private ProfileViews() {
    }

    /**
     * Perfil público. Com {@code privateProfile = true} (e quem vê não é o titular), vêm só nome, @, bolinha e selo: o
     * resto fica {@code null} e os posts não abrem ({@code 403}).
     */
    public record PublicProfileView(long id, String name, String handle, AccountBadge category, boolean verified,
                                    boolean privateProfile, String avatarUrl, String bio, List<String> pronouns,
                                    List<String> links, String city, String uf, Instant memberSince, Long postCount,
                                    String postsPath, String shareUrl) {
    }

    /** Card do feed de perfis: cor, glifo e ícone na {@code category}, selo em {@code verified}. */
    public record ProfileCardView(long id, String name, String handle, AccountBadge category, boolean verified,
                                  String avatarUrl, String bio, String city, String uf, String profilePath) {
    }

    /** {@code nextCursor} vai no parâmetro {@code cursor} da próxima página; {@code null} = fim. */
    public record ProfilePageView(List<ProfileCardView> items, String nextCursor) {
    }
}
