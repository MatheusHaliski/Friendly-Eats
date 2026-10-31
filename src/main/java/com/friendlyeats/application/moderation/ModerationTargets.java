package com.friendlyeats.application.moderation;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentService;
import com.friendlyeats.application.moderation.ModerationViews.TargetView;
import com.friendlyeats.application.post.PostReader;
import com.friendlyeats.application.profile.PublicProfileService;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostComment;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.moderation.ContentModeration;
import com.friendlyeats.domain.moderation.ModerationAction;
import com.friendlyeats.domain.moderation.TargetType;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostCommentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.security.SessionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * O que pode ser denunciado, como fica a cópia (snapshot) guardada no caso e como cada decisão muda o alvo (RF13).
 * Post e comentário ganham {@code moderation_state} (o texto fica guardado); avaliação usa o status do RF8
 * ({@code UNDER_REVIEW} sai do agregado); perfil vira conta suspensa; página de lugar vira {@code REMOVED}.
 */
@Component
public class ModerationTargets {

    static final int EXCERPT = 140;

    /** Alvo resolvido: dono (autor), lugar envolvido, cópia do conteúdo e onde abre no app web. */
    public record Target(TargetType type, String key, Long ownerId, Long establishmentId, Map<String, Object> snapshot,
                         String path, String state) {
    }

    private final PostReader posts;
    private final PostRepository postRepository;
    private final PostCommentRepository comments;
    private final ReviewRepository reviews;
    private final UserAccountRepository users;
    private final EstablishmentService establishmentService;
    private final EstablishmentRepository establishments;
    private final PublicProfileService profiles;
    private final SessionService sessions;
    private final JdbcTemplate jdbc;

    public ModerationTargets(PostReader posts, PostRepository postRepository, PostCommentRepository comments,
                             ReviewRepository reviews, UserAccountRepository users,
                             EstablishmentService establishmentService, EstablishmentRepository establishments,
                             PublicProfileService profiles, SessionService sessions, JdbcTemplate jdbc) {
        this.posts = posts;
        this.postRepository = postRepository;
        this.comments = comments;
        this.reviews = reviews;
        this.users = users;
        this.establishmentService = establishmentService;
        this.establishments = establishments;
        this.profiles = profiles;
        this.sessions = sessions;
        this.jdbc = jdbc;
    }

    /**
     * O alvo que {@code viewerId} (vazio = formulário público) pode denunciar: só o que ele consegue ver, com as regras
     * de cada página ({@code 404}/{@code 401} iguais às delas).
     */
    public Target forReport(TargetType type, String id, Optional<Long> viewerId) {
        Optional<UserAccount> viewer = viewerId.flatMap(users::findById);
        return switch (type) {
            case POST -> {
                Post p = posts.requireVisible(uuid(id), posts.viewer(viewerId));
                yield post(p);
            }
            case COMMENT -> {
                PostComment c = comments.findById(number(id)).filter(PostComment::isVisible)
                        .orElseThrow(() -> new CommonExceptions.NotFound("Comentário não encontrado."));
                Post p = posts.requireVisible(c.getPostId(), posts.viewer(viewerId));
                yield comment(c, p);
            }
            case REVIEW -> {
                Review r = reviews.findById(number(id)).filter(x -> x.getStatus() == Review.Status.PUBLISHED)
                        .orElseThrow(() -> new CommonExceptions.NotFound("Avaliação não encontrada."));
                Establishment e = establishmentService.requireVisible(r.getEstablishmentId(), viewer);
                yield review(r, e);
            }
            case PROFILE -> {
                profiles.profile(number(id), viewerId);   // 404 se o perfil não abre para quem denuncia
                yield profile(users.findById(number(id)).orElseThrow());
            }
            case ESTABLISHMENT -> establishment(establishmentService.requireVisible(number(id), viewer));
        };
    }

    /** O alvo como está agora, sem regra de visibilidade (para a moderação). Vazio = não existe mais. */
    public Optional<Target> current(TargetType type, String key) {
        try {
            return switch (type) {
                case POST -> postRepository.findById(uuid(key)).map(this::post);
                case COMMENT -> comments.findById(number(key))
                        .flatMap(c -> postRepository.findById(c.getPostId()).map(p -> comment(c, p)));
                case REVIEW -> reviews.findById(number(key))
                        .flatMap(r -> establishments.findById(r.getEstablishmentId()).map(e -> review(r, e)));
                case PROFILE -> users.findById(number(key)).map(this::profile);
                case ESTABLISHMENT -> establishments.findById(number(key)).map(this::establishment);
            };
        } catch (CommonExceptions.NotFound e) {
            return Optional.empty();
        }
    }

    /** Retirada preventiva (criança em risco ou sinal automático): some na hora, até a decisão. */
    public void preventive(TargetType type, String key) {
        switch (type) {
            case POST -> setPostState(key, ContentModeration.PREVENTIVE);
            case COMMENT -> setCommentState(number(key), ContentModeration.PREVENTIVE);
            case REVIEW -> reviews.findById(number(key)).ifPresent(Review::putUnderReview);
            default -> {
                // perfil e lugar não têm retirada preventiva: vão para o topo da fila
            }
        }
    }

    /** Aplica a decisão. {@code KEEP} e {@code WARN} deixam (ou devolvem) o alvo no ar. */
    public void apply(TargetType type, String key, ModerationAction action) {
        boolean restore = !action.takesDown();
        switch (type) {
            case POST -> setPostState(key, restore ? null
                    : action == ModerationAction.HIDE ? ContentModeration.HIDDEN : ContentModeration.REMOVED);
            case COMMENT -> setCommentState(number(key), restore ? null
                    : action == ModerationAction.HIDE ? ContentModeration.HIDDEN : ContentModeration.REMOVED);
            case REVIEW -> reviews.findById(number(key)).ifPresent(r -> {
                if (restore) {
                    r.restoreByModeration();
                } else {
                    r.removeByModeration();
                }
            });
            case PROFILE -> users.findById(number(key)).ifPresent(u -> {
                if (!restore && u.getAccountStatus() == AccountStatus.ACTIVE) {
                    u.suspend();
                    sessions.revokeAll(u.getId(), null);
                } else if (restore && u.getAccountStatus() == AccountStatus.SUSPENDED) {
                    u.activate();
                }
            });
            case ESTABLISHMENT -> establishments.findById(number(key)).ifPresent(e -> {
                if (!restore) {
                    e.setStatus(Establishment.Status.REMOVED);
                } else if (e.getStatus() == Establishment.Status.REMOVED) {
                    e.setStatus(Establishment.Status.ACTIVE);
                }
            });
        }
    }

    public TargetView view(TargetType type, String key, Map<String, Object> snapshot, Optional<Target> now) {
        return new TargetView(type.name(), type.getLabel(), key, now.map(Target::path).orElse(null), excerpt(snapshot));
    }

    // ------------------------------------------------------------ cópias

    private Target post(Post p) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("kind", p.getKind().getLabel());
        s.put("title", p.getTitle());
        put(s, "body", p.getBody());
        put(s, "altText", p.getAltText());
        put(s, "mediaObjectKey", p.getMediaObjectKey());
        return new Target(TargetType.POST, p.getId().toString(), p.getAuthorId(), p.getEstablishmentId(), s,
                "/posts/" + p.getId(), postState(p));
    }

    private Target comment(PostComment c, Post p) {
        Map<String, Object> s = new LinkedHashMap<>();
        put(s, "body", c.getBody());
        s.put("postId", p.getId().toString());
        s.put("postTitle", p.getTitle());
        String state = c.getModerationState() != null ? stateLabel(c.getModerationState())
                : c.getStatus().name().equals("REMOVED") ? "Apagado" : "No ar";
        return new Target(TargetType.COMMENT, c.getId().toString(), c.getAuthorId(), null, s,
                "/posts/" + p.getId() + "#comentarios", state);
    }

    private Target review(Review r, Establishment e) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("rating", r.getRating());
        put(s, "body", r.getBody());
        s.put("establishment", e.getName());
        s.put("trust", r.getTrust().name());
        String state = switch (r.getStatus()) {
            case PUBLISHED -> "No ar";
            case UNDER_REVIEW -> "Em análise (fora da nota)";
            case REMOVED -> "Removida";
        };
        return new Target(TargetType.REVIEW, r.getId().toString(), r.getAuthorId(), e.getId(), s,
                "/lugares/" + e.getId(), state);
    }

    private Target profile(UserAccount u) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", u.getName());
        put(s, "handle", u.getHandle());
        put(s, "bio", u.getBio());
        String state = switch (u.getAccountStatus()) {
            case ACTIVE -> "No ar";
            case SUSPENDED -> "Conta suspensa";
            default -> "Fora do ar";
        };
        return new Target(TargetType.PROFILE, u.getId().toString(), u.getId(), null, s, "/u/" + u.getId(), state);
    }

    private Target establishment(Establishment e) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", e.getName());
        put(s, "description", e.getDescription());
        String state = e.getStatus() == Establishment.Status.REMOVED ? "Página fora do ar" : "No ar";
        return new Target(TargetType.ESTABLISHMENT, e.getId().toString(), e.getOwnerId(), e.getId(), s,
                "/lugares/" + e.getId(), state);
    }

    private static String postState(Post p) {
        if (p.getModerationState() != null) {
            return stateLabel(p.getModerationState());
        }
        return switch (p.getStatus()) {
            case PUBLISHED -> "No ar";
            case ARCHIVED -> "Arquivado";
            case DRAFT -> "Rascunho";
            case REMOVED -> "Excluído";
        };
    }

    private static String stateLabel(String moderationState) {
        return switch (ContentModeration.valueOf(moderationState)) {
            case PREVENTIVE -> "Retirado preventivamente";
            case HIDDEN -> "Ocultado pela moderação";
            case REMOVED -> "Removido pela moderação";
        };
    }

    static String excerpt(Map<String, Object> snapshot) {
        for (String k : List.of("title", "body", "name", "description")) {
            Object v = snapshot.get(k);
            if (v != null && !v.toString().isBlank()) {
                String s = v.toString().strip();
                return s.length() <= EXCERPT ? s : s.substring(0, EXCERPT - 1).stripTrailing() + "…";
            }
        }
        return null;
    }

    private static void put(Map<String, Object> m, String k, Object v) {
        if (v != null) {
            m.put(k, v);
        }
    }

    // ------------------------------------------------------------ escrita

    private void setPostState(String key, ContentModeration state) {
        jdbc.update("UPDATE post SET moderation_state = ? WHERE id = ?", state == null ? null : state.name(), uuid(key));
    }

    /** O contador de comentários desce ao tirar do ar e sobe ao devolver (só comentário não apagado). */
    private void setCommentState(long id, ContentModeration state) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT post_id, status, moderation_state FROM post_comment WHERE id = ? FOR UPDATE", id);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> row = rows.getFirst();
        boolean wasLive = row.get("moderation_state") == null;
        boolean willBeLive = state == null;
        jdbc.update("UPDATE post_comment SET moderation_state = ? WHERE id = ?", state == null ? null : state.name(), id);
        if ("VISIBLE".equals(row.get("status")) && wasLive != willBeLive) {
            postRepository.addToCommentCount((UUID) row.get("post_id"), willBeLive ? 1 : -1);
        }
    }

    static UUID uuid(String id) {
        try {
            return UUID.fromString(id);
        } catch (RuntimeException e) {
            throw new CommonExceptions.NotFound("Conteúdo não encontrado.");
        }
    }

    static long number(String id) {
        try {
            return Long.parseLong(id);
        } catch (RuntimeException e) {
            throw new CommonExceptions.NotFound("Conteúdo não encontrado.");
        }
    }
}
