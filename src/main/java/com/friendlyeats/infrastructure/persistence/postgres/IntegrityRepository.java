package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.moderation.AlertKind;
import com.friendlyeats.domain.moderation.ReviewSignal;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Integridade das avaliações (V5 · RF13): sinais de fraude, alertas ao consumidor e congelamento de novas
 * avaliações. As consultas de sinal olham só o mesmo lugar e uma janela curta de tempo.
 */
@Repository
public class IntegrityRepository {

    public record AlertRow(long id, long establishmentId, AlertKind kind, String message, boolean freezeReviews,
                           Instant startsAt, Instant endsAt, Instant liftedAt, boolean automated, Long createdBy) {

        public boolean activeAt(Instant now) {
            return liftedAt == null && !startsAt.isAfter(now) && (endsAt == null || endsAt.isAfter(now));
        }
    }

    public record SignalRow(long reviewId, ReviewSignal code, String detail, Instant createdAt) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public IntegrityRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ sinais

    /**
     * Avaliações do lugar escritas desde {@code since} por contas criadas há menos de {@code newAccountDays} dias
     * (em relação ao momento da avaliação): a "rajada de contas novas".
     */
    public List<Long> recentFromNewAccounts(long establishmentId, Instant since, int newAccountDays) {
        return jdbc.queryForList("""
                SELECT r.id FROM review r JOIN user_account u ON u.id = r.author_id
                WHERE r.establishment_id = :establishment AND r.created_at >= :since
                  AND u.created_at > r.created_at - make_interval(days => :days)
                  AND r.status <> 'REMOVED'
                ORDER BY r.id
                """, new MapSqlParameterSource().addValue("establishment", establishmentId)
                .addValue("since", Timestamp.from(since)).addValue("days", newAccountDays), Long.class);
    }

    /** Outras avaliações do lugar, de outras contas, com o mesmo HMAC de IP desde {@code since}. */
    public List<Long> sameOrigin(long establishmentId, long reviewId, long authorId, String originHash, Instant since) {
        return jdbc.queryForList("""
                SELECT id FROM review
                WHERE establishment_id = :establishment AND origin_hash = :origin AND id <> :review
                  AND author_id <> :author AND created_at >= :since AND status <> 'REMOVED'
                ORDER BY id
                """, new MapSqlParameterSource().addValue("establishment", establishmentId).addValue("origin", originHash)
                .addValue("review", reviewId).addValue("author", authorId).addValue("since", Timestamp.from(since)),
                Long.class);
    }

    public boolean addSignal(long reviewId, ReviewSignal code, String detail) {
        return jdbc.update("""
                INSERT INTO review_signal (review_id, code, detail) VALUES (:review, :code, :detail)
                ON CONFLICT (review_id, code) DO NOTHING
                """, new MapSqlParameterSource().addValue("review", reviewId).addValue("code", code.name())
                .addValue("detail", detail)) == 1;
    }

    public List<SignalRow> signalsOf(long reviewId) {
        return jdbc.query("SELECT review_id, code, detail, created_at FROM review_signal WHERE review_id = :review ORDER BY id",
                Map.of("review", reviewId), (rs, i) -> new SignalRow(rs.getLong(1), ReviewSignal.valueOf(rs.getString(2)),
                        rs.getString(3), rs.getTimestamp(4).toInstant()));
    }

    // ------------------------------------------------------------ alertas e congelamento

    public long insertAlert(long establishmentId, AlertKind kind, String message, boolean freeze, Instant startsAt,
                            Instant endsAt, boolean automated, Long createdBy) {
        Long id = jdbc.queryForObject("""
                INSERT INTO establishment_alert (establishment_id, kind, message, freeze_reviews, starts_at, ends_at,
                                                 automated, created_by)
                VALUES (:establishment, :kind, :message, :freeze, :starts, :ends, :automated, :by)
                RETURNING id
                """, new MapSqlParameterSource().addValue("establishment", establishmentId).addValue("kind", kind.name())
                .addValue("message", message).addValue("freeze", freeze).addValue("starts", Timestamp.from(startsAt))
                .addValue("ends", endsAt == null ? null : Timestamp.from(endsAt)).addValue("automated", automated)
                .addValue("by", createdBy), Long.class);
        return id == null ? 0 : id;
    }

    public boolean liftAlert(long establishmentId, long alertId, Instant when) {
        return jdbc.update("""
                UPDATE establishment_alert SET lifted_at = :when
                WHERE id = :id AND establishment_id = :establishment AND lifted_at IS NULL
                """, new MapSqlParameterSource().addValue("when", Timestamp.from(when)).addValue("id", alertId)
                .addValue("establishment", establishmentId)) == 1;
    }

    public Optional<AlertRow> findAlert(long alertId) {
        return jdbc.query("SELECT * FROM establishment_alert WHERE id = :id", Map.of("id", alertId), this::alertRow)
                .stream().findFirst();
    }

    /** Histórico público do lugar, mais recentes primeiro. */
    public List<AlertRow> alertsOf(long establishmentId) {
        return jdbc.query("""
                SELECT * FROM establishment_alert WHERE establishment_id = :establishment
                ORDER BY starts_at DESC, id DESC LIMIT 50
                """, Map.of("establishment", establishmentId), this::alertRow);
    }

    /** Fim do congelamento em vigor (o mais distante), se houver. */
    public Optional<Instant> frozenUntil(long establishmentId, Instant now) {
        Timestamp t = jdbc.queryForObject("""
                SELECT max(ends_at) FROM establishment_alert
                WHERE establishment_id = :establishment AND freeze_reviews AND lifted_at IS NULL
                  AND starts_at <= :now AND ends_at > :now
                """, new MapSqlParameterSource().addValue("establishment", establishmentId)
                .addValue("now", Timestamp.from(now)), Timestamp.class);
        return Optional.ofNullable(t).map(Timestamp::toInstant);
    }

    private AlertRow alertRow(ResultSet rs, int i) throws SQLException {
        Timestamp ends = rs.getTimestamp("ends_at");
        Timestamp lifted = rs.getTimestamp("lifted_at");
        long by = rs.getLong("created_by");
        Long createdBy = rs.wasNull() ? null : by;
        return new AlertRow(rs.getLong("id"), rs.getLong("establishment_id"), AlertKind.valueOf(rs.getString("kind")),
                rs.getString("message"), rs.getBoolean("freeze_reviews"), rs.getTimestamp("starts_at").toInstant(),
                ends == null ? null : ends.toInstant(), lifted == null ? null : lifted.toInstant(),
                rs.getBoolean("automated"), createdBy);
    }
}
