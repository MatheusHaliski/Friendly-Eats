package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.PostKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Consultas do feed (RF14). Devolvem só os ids, na ordem do feed; o serviço carrega os posts e monta os cards.
 *
 * <p>A regra de quem vê está toda em {@link #VISIBLE}, a mesma da página do post: publicado, fora do prazo de
 * expiração, autor com a conta ativa e perfil público (o próprio autor sempre se vê) e, para visitante sem login ou
 * menor de 18, só posts {@code LIVRE}. Sem linha em {@code user_preference}, o perfil é privado para menor de 16 (o
 * padrão do cadastro, ECA Digital). Post retirado pela moderação não entra (RF13), nem post de quem bloqueou quem vê,
 * foi bloqueado por ele ou foi silenciado por ele (RF19).
 */
@Repository
public class PostFeedQueries {

    static final String VISIBLE = """
            p.status = 'PUBLISHED'
              AND (p.expires_at IS NULL OR p.expires_at > :now)
              AND u.account_status = 'ACTIVE'
              AND (p.author_id = :viewer
                   OR NOT COALESCE(pp.profile_private, u.user_date_of_birth > :minorBirthAfter, FALSE))
              AND p.moderation_state IS NULL
              AND NOT EXISTS (SELECT 1 FROM user_block b
                              WHERE (b.blocker_id = :viewer AND b.blocked_id = p.author_id)
                                 OR (b.blocker_id = p.author_id AND b.blocked_id = :viewer))
              AND NOT EXISTS (SELECT 1 FROM user_mute m WHERE m.muter_id = :viewer AND m.muted_id = p.author_id)
            """;

    private static final String FROM = """
            FROM post p
            JOIN user_account u ON u.id = p.author_id
            LEFT JOIN user_preference pp ON pp.user_id = p.author_id
            """;

    /** Quem está vendo: {@code viewerId} nulo = visitante; {@code adult} decide os posts 18+. */
    public record Viewer(Long viewerId, boolean adult, LocalDate minorBirthAfter, Instant now) {
    }

    /** Filtros opcionais do feed; o cursor é o último (created_at, id) da página anterior. */
    public record Filter(FeedType feed, PostKind kind, Long establishmentId, Instant beforeCreatedAt, UUID beforeId) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public PostFeedQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<UUID> page(Filter f, Viewer v, int limit) {
        MapSqlParameterSource params = params(v).addValue("limit", limit);
        StringBuilder sql = new StringBuilder("SELECT p.id ").append(FROM).append("WHERE ").append(VISIBLE);
        restrictAge(sql, v);
        if (f.feed() != null) {
            sql.append(" AND p.feed_type = :feed");
            params.addValue("feed", f.feed().name());
        }
        if (f.kind() != null) {
            sql.append(" AND p.kind = :kind");
            params.addValue("kind", f.kind().name());
        }
        if (f.establishmentId() != null) {
            sql.append(" AND p.establishment_id = :establishment");
            params.addValue("establishment", f.establishmentId());
        }
        if (f.beforeCreatedAt() != null) {
            sql.append(" AND (p.created_at, p.id) < (:beforeCreatedAt, :beforeId)");
            params.addValue("beforeCreatedAt", Timestamp.from(f.beforeCreatedAt())).addValue("beforeId", f.beforeId());
        }
        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT :limit");
        return jdbc.queryForList(sql.toString(), params, UUID.class);
    }

    /** Destes ids, quais o {@code viewer} pode ver no feed (usado pelo ranking "em alta", que vem do Valkey). */
    public List<UUID> visible(Collection<UUID> ids, Viewer v) {
        if (ids.isEmpty()) {
            return List.of();
        }
        MapSqlParameterSource params = params(v).addValue("ids", ids);
        StringBuilder sql = new StringBuilder("SELECT p.id ").append(FROM).append("WHERE p.id IN (:ids) AND ")
                .append(VISIBLE);
        restrictAge(sql, v);
        return jdbc.queryForList(sql.toString(), params, UUID.class);
    }

    private static void restrictAge(StringBuilder sql, Viewer v) {
        if (!v.adult()) {
            sql.append(" AND p.age_rating = 'LIVRE'");
        }
    }

    private static MapSqlParameterSource params(Viewer v) {
        return new MapSqlParameterSource()
                .addValue("now", Timestamp.from(v.now()))
                .addValue("viewer", v.viewerId() == null ? -1L : v.viewerId())
                .addValue("minorBirthAfter", v.minorBirthAfter());
    }
}
