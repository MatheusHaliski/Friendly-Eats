package com.friendlyeats.application.post;

import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserPreference.CommentAudience;

/**
 * Quem pode comentar num post (RF12). Vale o {@code who_can_comment} de quem publicou: {@code EVERYONE} abre para
 * qualquer conta ativa; {@code NOBODY} fecha para os outros; {@code FOLLOWERS} hoje também fecha, porque seguir perfis
 * ainda não existe (⏳). Menor de 16 nasce com {@code FOLLOWERS} e não pode abrir (ECA Digital), então ninguém de fora
 * comenta no post dele. O autor sempre comenta na própria caixa.
 */
public final class CommentPolicy {

    /** Resultado: {@code reason} nulo = pode; {@code status} diz se a recusa é 401, 403 ou 409. */
    public record Decision(int status, String reason) {
        static final Decision ALLOWED = new Decision(200, null);

        public boolean allowed() {
            return reason == null;
        }
    }

    private CommentPolicy() {
    }

    public static Decision decide(Post post, CommentAudience audience, UserAccount viewer) {
        if (!post.acceptsInteractions()) {
            return new Decision(409, "Este post está arquivado e não recebe mais comentários.");
        }
        if (post.getKind() == PostKind.ALERTA_SANITARIO) {
            return new Decision(409, "Alerta sanitário não recebe curtidas nem comentários: compartilhe ou salve.");
        }
        if (viewer == null) {
            return new Decision(401, "Entre na sua conta para comentar.");
        }
        if (!AuthorDirectory.isActive(viewer)) {
            return new Decision(403, "Sua conta precisa estar ativa para comentar. Confirme seu e-mail ou fale com o "
                    + "suporte.");
        }
        if (post.isAuthoredBy(viewer.getId())) {
            return Decision.ALLOWED;
        }
        return switch (audience) {
            case EVERYONE -> Decision.ALLOWED;
            case NOBODY -> new Decision(403, "Quem publicou fechou os comentários deste post.");
            case FOLLOWERS -> new Decision(403, "Quem publicou aceita comentários só de quem segue o perfil, e seguir "
                    + "perfis ainda não está disponível.");
        };
    }
}
