package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.UserCategoryEnum;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Feed de perfis (RF15, {@code FeedType.PROFILE}): só contas ativas com o perfil público. Organizações (estabelecimento,
 * varejo, oficial) entram só com o selo conferido, para uma conta "Anvisa" sem selo não aparecer na vitrine.
 *
 * <p>A cidade filtrada é a do cadastro do tipo de conta ({@code type_details.city}: a praça de atuação), nunca a de
 * residência; para o comensal ela não é pública, então o filtro por cidade não o alcança (LGPD Art. 6º, III).
 */
@Repository
public class ProfileDirectoryQueries {

    public record Filter(UserCategoryEnum type, String city, String q, Long beforeId) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public ProfileDirectoryQueries(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Ids das contas, das mais novas para as mais antigas (paginação por id). */
    public List<Long> page(Filter f, LocalDate minorBirthAfter, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("minorBirthAfter", minorBirthAfter).addValue("limit", limit);
        StringBuilder sql = new StringBuilder("""
                SELECT u.id FROM user_account u
                LEFT JOIN user_preference pp ON pp.user_id = u.id
                WHERE u.account_status = 'ACTIVE'
                  AND NOT COALESCE(pp.profile_private, u.user_date_of_birth > :minorBirthAfter, FALSE)
                  AND (u.user_type NOT IN ('ESTABELECIMENTO','VAREJO','OFICIAL') OR u.category_verification = 'VERIFIED')
                """);
        if (f.type() != null) {
            sql.append(" AND u.user_type = :type");
            params.addValue("type", f.type().name());
        }
        if (f.city() != null) {
            sql.append(" AND u.user_type <> 'COMENSAL'"
                    + " AND lower(f_unaccent(u.type_details->>'city')) = lower(f_unaccent(:city))");
            params.addValue("city", f.city());
        }
        if (f.q() != null) {
            sql.append(" AND (lower(f_unaccent(u.name)) LIKE lower(f_unaccent(:q)) ESCAPE '\\'"
                    + " OR u.handle LIKE lower(:q) ESCAPE '\\')");
            params.addValue("q", "%" + f.q().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
        }
        if (f.beforeId() != null) {
            sql.append(" AND u.id < :beforeId");
            params.addValue("beforeId", f.beforeId());
        }
        sql.append(" ORDER BY u.id DESC LIMIT :limit");
        return jdbc.queryForList(sql.toString(), params, Long.class);
    }
}
