package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.moderation.CaseStatus;
import com.friendlyeats.domain.moderation.ModerationAction;
import com.friendlyeats.domain.moderation.ReportReason;
import com.friendlyeats.domain.moderation.ReportSource;
import com.friendlyeats.domain.moderation.TargetType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Fila de moderação (V5 · RF13): casos, denúncias, decisões (só inclusão: o banco recusa UPDATE e DELETE) e
 * recursos. Consultas em SQL direto; as regras ficam no {@code ModerationService} e no {@code ReportService}.
 */
@Repository
public class ModerationRepository {

    public record CaseRow(long id, TargetType targetType, String targetKey, Long targetOwnerId, Long establishmentId,
                          CaseStatus status, int priority, ReportReason topReason, int reportCount, boolean preventive,
                          String authorityReferral, String authorityReference, Instant authoritySentAt,
                          Map<String, Object> snapshot, Instant dueAt, Instant createdAt, Instant updatedAt,
                          Instant decidedAt) {
    }

    public record ReportRow(long id, long caseId, ReportSource source, Long reporterId, String reporterName,
                            String reporterEmail, ReportReason reason, String details, boolean childSexualAbuse,
                            boolean lowTrust, String status, Instant createdAt, Instant resolvedAt) {
    }

    public record DecisionRow(long id, long caseId, String stage, ModerationAction action, ReportReason policyReason,
                              String statement, boolean automated, Long moderatorId, Instant createdAt) {
    }

    public record AppealRow(long id, long caseId, long decisionId, Long appellantId, String role, String reason,
                            String status, Long resultDecisionId, Instant createdAt, Instant resolvedAt) {
    }

    /** Uma denúncia de quem denunciou, com o caso dela. */
    public record MyReportRow(ReportRow report, CaseRow caseRow) {
    }

    /** Contagem agregada para o relatório. */
    public record Count(String code, long count) {
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final JsonMapper json;

    public ModerationRepository(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    // ------------------------------------------------------------ casos

    /**
     * Abre o caso do alvo ou devolve o aberto. Duas denúncias ao mesmo tempo sobre o mesmo alvo caem no mesmo caso
     * (índice único parcial + {@code ON CONFLICT DO NOTHING}).
     */
    public long openCase(TargetType type, String key, Long ownerId, Long establishmentId, int priority,
                         ReportReason reason, Map<String, Object> snapshot, Instant dueAt) {
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("type", type.name()).addValue("key", key).addValue("owner", ownerId)
                .addValue("establishment", establishmentId).addValue("priority", priority)
                .addValue("reason", reason.name()).addValue("snapshot", json.writeValueAsString(snapshot))
                .addValue("due", Timestamp.from(dueAt));
        List<Long> created = jdbc.queryForList("""
                INSERT INTO moderation_case (target_type, target_key, target_owner_id, establishment_id, priority,
                                             top_reason, snapshot, due_at)
                VALUES (:type, :key, :owner, :establishment, :priority, :reason, CAST(:snapshot AS jsonb), :due)
                ON CONFLICT (target_type, target_key) WHERE status = 'OPEN' DO NOTHING
                RETURNING id
                """, p, Long.class);
        if (!created.isEmpty()) {
            return created.getFirst();
        }
        return findOpenCase(type, key).map(CaseRow::id)
                .orElseThrow(() -> new IllegalStateException("caso aberto sumiu durante a denúncia"));
    }

    /** Soma uma denúncia ao caso: fica com a maior prioridade, o menor prazo e o motivo mais grave. */
    public void addReportToCase(long caseId, int priority, ReportReason reason, Instant dueAt, boolean preventive,
                                boolean authority) {
        jdbc.update("""
                UPDATE moderation_case SET
                    report_count = report_count + 1,
                    top_reason = CASE WHEN :priority < priority THEN :reason ELSE top_reason END,
                    priority = LEAST(priority, :priority),
                    due_at = LEAST(due_at, :due),
                    preventive = preventive OR :preventive,
                    authority_referral = CASE WHEN :authority AND authority_referral = 'NONE' THEN 'PENDING'
                                              ELSE authority_referral END,
                    updated_at = now()
                WHERE id = :id
                """, new MapSqlParameterSource().addValue("id", caseId).addValue("priority", priority)
                .addValue("reason", reason.name()).addValue("due", Timestamp.from(dueAt))
                .addValue("preventive", preventive).addValue("authority", authority));
    }

    public Optional<CaseRow> findCase(long id) {
        return jdbc.query("SELECT * FROM moderation_case WHERE id = :id", Map.of("id", id), this::caseRow)
                .stream().findFirst();
    }

    public Optional<CaseRow> findOpenCase(TargetType type, String key) {
        return jdbc.query("SELECT * FROM moderation_case WHERE target_type = :type AND target_key = :key AND status = 'OPEN'",
                Map.of("type", type.name(), "key", key), this::caseRow).stream().findFirst();
    }

    /** A fila: abertos e em recurso por prioridade e prazo; decididos e encerrados pelos mais recentes. */
    public List<CaseRow> queue(CaseStatus status, int offset, int limit) {
        String order = status == CaseStatus.OPEN || status == CaseStatus.IN_APPEAL
                ? "priority, due_at, id" : "updated_at DESC, id DESC";
        return jdbc.query("SELECT * FROM moderation_case WHERE status = :status ORDER BY " + order
                        + " OFFSET :offset LIMIT :limit",
                new MapSqlParameterSource().addValue("status", status.name()).addValue("offset", offset)
                        .addValue("limit", limit), this::caseRow);
    }

    public long countByStatus(CaseStatus status) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM moderation_case WHERE status = :status",
                Map.of("status", status.name()), Long.class);
        return n == null ? 0 : n;
    }

    public void setCaseStatus(long caseId, CaseStatus status, boolean decided) {
        jdbc.update("UPDATE moderation_case SET status = :status, updated_at = now()"
                        + (decided ? ", decided_at = now()" : "") + " WHERE id = :id",
                Map.of("status", status.name(), "id", caseId));
    }

    public void setPreventive(long caseId, boolean preventive) {
        jdbc.update("UPDATE moderation_case SET preventive = :p, updated_at = now() WHERE id = :id",
                Map.of("p", preventive, "id", caseId));
    }

    public void markAuthorityPending(long caseId) {
        jdbc.update("""
                UPDATE moderation_case SET authority_referral = 'PENDING', updated_at = now()
                WHERE id = :id AND authority_referral = 'NONE'
                """, Map.of("id", caseId));
    }

    public void recordAuthorityReferral(long caseId, String reference) {
        jdbc.update("""
                UPDATE moderation_case SET authority_referral = 'SENT', authority_reference = :ref,
                                           authority_sent_at = now(), updated_at = now()
                WHERE id = :id
                """, Map.of("id", caseId, "ref", reference));
    }

    /** Casos sobre conteúdo da pessoa que tiveram medida (retirada, ocultação ou aviso), dos mais recentes. */
    public List<CaseRow> casesWithMeasureOwnedBy(long ownerId) {
        return jdbc.query("""
                SELECT c.* FROM moderation_case c
                WHERE c.target_owner_id = :owner
                  AND EXISTS (SELECT 1 FROM moderation_decision d WHERE d.case_id = c.id AND d.action <> 'KEEP')
                ORDER BY c.updated_at DESC, c.id DESC LIMIT 100
                """, Map.of("owner", ownerId), this::caseRow);
    }

    // ------------------------------------------------------------ denúncias

    public long insertReport(long caseId, ReportSource source, Long reporterId, String reporterName,
                             String reporterEmail, ReportReason reason, String details, boolean childSexualAbuse,
                             boolean lowTrust, String ipHash) {
        Long id = jdbc.queryForObject("""
                INSERT INTO moderation_report (case_id, source, reporter_id, reporter_name, reporter_email, reason,
                                               details, child_sexual_abuse, low_trust, ip_hash)
                VALUES (:case, :source, :reporter, :name, :email, :reason, :details, :csa, :low, :ip)
                RETURNING id
                """, new MapSqlParameterSource().addValue("case", caseId).addValue("source", source.name())
                .addValue("reporter", reporterId).addValue("name", reporterName).addValue("email", reporterEmail)
                .addValue("reason", reason.name()).addValue("details", details).addValue("csa", childSexualAbuse)
                .addValue("low", lowTrust).addValue("ip", ipHash), Long.class);
        return id == null ? 0 : id;
    }

    public boolean hasOpenReport(long caseId, long reporterId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM moderation_report
                               WHERE case_id = :case AND reporter_id = :reporter AND status = 'OPEN')
                """, Map.of("case", caseId, "reporter", reporterId), Boolean.class));
    }

    public List<ReportRow> reportsOf(long caseId) {
        return jdbc.query("SELECT * FROM moderation_report WHERE case_id = :case ORDER BY id",
                Map.of("case", caseId), this::reportRow);
    }

    public void resolveReports(long caseId, String status) {
        jdbc.update("""
                UPDATE moderation_report SET status = :status, resolved_at = now()
                WHERE case_id = :case AND status = 'OPEN'
                """, Map.of("status", status, "case", caseId));
    }

    /** Recurso revertido: o resultado das denúncias já resolvidas acompanha a nova decisão. */
    public void setReportsOutcome(long caseId, String status) {
        jdbc.update("UPDATE moderation_report SET status = :status WHERE case_id = :case AND status <> 'OPEN'",
                Map.of("status", status, "case", caseId));
    }

    public List<MyReportRow> reportsBy(long reporterId) {
        return jdbc.query("""
                SELECT r.*, c.* FROM moderation_report r JOIN moderation_case c ON c.id = r.case_id
                WHERE r.reporter_id = :reporter ORDER BY r.created_at DESC, r.id DESC LIMIT 100
                """, Map.of("reporter", reporterId), (rs, i) -> new MyReportRow(reportRow(rs, i), caseRowAt(rs, 14)));
    }

    /** Denúncias (exceto sinais automáticos) feitas pela conta desde {@code since}: limite de ritmo. */
    public long countReportsBy(long reporterId, Instant since) {
        return count("""
                SELECT count(*) FROM moderation_report
                WHERE reporter_id = :reporter AND source IN ('USER','OWNER_CONTEST') AND created_at >= :since
                """, new MapSqlParameterSource().addValue("reporter", reporterId).addValue("since", Timestamp.from(since)));
    }

    public long countPublicReportsFrom(String ipHash, Instant since) {
        return count("""
                SELECT count(*) FROM moderation_report
                WHERE source = 'PUBLIC_FORM' AND ip_hash = :ip AND created_at >= :since
                """, new MapSqlParameterSource().addValue("ip", ipHash).addValue("since", Timestamp.from(since)));
    }

    /** Contestações do dono para avaliações de um lugar desde {@code since} (limite por mês). */
    public long countOwnerContests(long establishmentId, Instant since) {
        return count("""
                SELECT count(*) FROM moderation_report r JOIN moderation_case c ON c.id = r.case_id
                WHERE r.source = 'OWNER_CONTEST' AND c.establishment_id = :establishment AND r.created_at >= :since
                """, new MapSqlParameterSource().addValue("establishment", establishmentId)
                .addValue("since", Timestamp.from(since)));
    }

    /** {@code [improcedentes, resolvidas]} da conta desde {@code since}: base da confiabilidade de quem denuncia. */
    public long[] reporterTrack(long reporterId, Instant since) {
        return jdbc.queryForObject("""
                SELECT count(*) FILTER (WHERE status = 'DISMISSED'), count(*) FILTER (WHERE status <> 'OPEN')
                FROM moderation_report
                WHERE reporter_id = :reporter AND source IN ('USER','OWNER_CONTEST') AND created_at >= :since
                """, new MapSqlParameterSource().addValue("reporter", reporterId).addValue("since", Timestamp.from(since)),
                (rs, i) -> new long[]{rs.getLong(1), rs.getLong(2)});
    }

    // ------------------------------------------------------------ decisões (só inclusão)

    public long insertDecision(long caseId, String stage, ModerationAction action, ReportReason policy,
                               String statement, boolean automated, Long moderatorId) {
        Long id = jdbc.queryForObject("""
                INSERT INTO moderation_decision (case_id, stage, action, policy_reason, statement, automated, moderator_id)
                VALUES (:case, :stage, :action, :policy, :statement, :automated, :moderator)
                RETURNING id
                """, new MapSqlParameterSource().addValue("case", caseId).addValue("stage", stage)
                .addValue("action", action.name()).addValue("policy", policy == null ? null : policy.name())
                .addValue("statement", statement).addValue("automated", automated).addValue("moderator", moderatorId),
                Long.class);
        return id == null ? 0 : id;
    }

    public List<DecisionRow> decisionsOf(long caseId) {
        return jdbc.query("SELECT * FROM moderation_decision WHERE case_id = :case ORDER BY id",
                Map.of("case", caseId), this::decisionRow);
    }

    // ------------------------------------------------------------ recursos

    public long insertAppeal(long caseId, long decisionId, long appellantId, String role, String reason) {
        Long id = jdbc.queryForObject("""
                INSERT INTO moderation_appeal (case_id, decision_id, appellant_id, appellant_role, reason)
                VALUES (:case, :decision, :appellant, :role, :reason)
                RETURNING id
                """, new MapSqlParameterSource().addValue("case", caseId).addValue("decision", decisionId)
                .addValue("appellant", appellantId).addValue("role", role).addValue("reason", reason), Long.class);
        return id == null ? 0 : id;
    }

    public List<AppealRow> appealsOf(long caseId) {
        return jdbc.query("SELECT * FROM moderation_appeal WHERE case_id = :case ORDER BY id",
                Map.of("case", caseId), this::appealRow);
    }

    public void resolveAppeals(long caseId, String status, long resultDecisionId) {
        jdbc.update("""
                UPDATE moderation_appeal SET status = :status, result_decision_id = :result, resolved_at = now()
                WHERE case_id = :case AND status = 'OPEN'
                """, Map.of("status", status, "result", resultDecisionId, "case", caseId));
    }

    // ------------------------------------------------------------ relatório

    public List<Count> reportsByReason(Instant from, Instant to) {
        return counts("SELECT reason, count(*) FROM moderation_report WHERE created_at >= :from AND created_at < :to "
                + "GROUP BY reason ORDER BY 2 DESC, 1", from, to);
    }

    public List<Count> reportsBySource(Instant from, Instant to) {
        return counts("SELECT source, count(*) FROM moderation_report WHERE created_at >= :from AND created_at < :to "
                + "GROUP BY source ORDER BY 2 DESC, 1", from, to);
    }

    public List<Count> decisionsByAction(Instant from, Instant to) {
        return counts("SELECT stage || ':' || action || ':' || CASE WHEN automated THEN 'A' ELSE 'H' END, count(*) "
                + "FROM moderation_decision WHERE created_at >= :from AND created_at < :to GROUP BY 1 ORDER BY 1", from, to);
    }

    public List<Count> decisionsByPolicy(Instant from, Instant to) {
        return counts("SELECT policy_reason, count(*) FROM moderation_decision WHERE created_at >= :from "
                + "AND created_at < :to AND policy_reason IS NOT NULL AND stage <> 'PREVENTIVE' GROUP BY 1 ORDER BY 2 DESC, 1",
                from, to);
    }

    public List<Count> appealsByStatus(Instant from, Instant to) {
        return counts("SELECT status, count(*) FROM moderation_appeal WHERE created_at >= :from AND created_at < :to "
                + "GROUP BY status ORDER BY 1", from, to);
    }

    public List<Count> authorityReferrals(Instant from, Instant to) {
        return counts("SELECT authority_referral, count(*) FROM moderation_case WHERE authority_referral <> 'NONE' "
                + "AND created_at >= :from AND created_at < :to GROUP BY 1 ORDER BY 1", from, to);
    }

    public long overdue(Instant now) {
        return count("SELECT count(*) FROM moderation_case WHERE status IN ('OPEN','IN_APPEAL') AND due_at < :now",
                new MapSqlParameterSource().addValue("now", Timestamp.from(now)));
    }

    // ------------------------------------------------------------ apoio

    private List<Count> counts(String sql, Instant from, Instant to) {
        return jdbc.query(sql, new MapSqlParameterSource().addValue("from", Timestamp.from(from))
                .addValue("to", Timestamp.from(to)), (rs, i) -> new Count(rs.getString(1), rs.getLong(2)));
    }

    private long count(String sql, MapSqlParameterSource p) {
        Long n = jdbc.queryForObject(sql, p, Long.class);
        return n == null ? 0 : n;
    }

    private CaseRow caseRow(ResultSet rs, int i) throws SQLException {
        return caseRowAt(rs, 0);
    }

    /** Lê as colunas do caso a partir da posição {@code base} (0 = consulta só da tabela de casos). */
    private CaseRow caseRowAt(ResultSet rs, int base) throws SQLException {
        String snapshot = rs.getString(base + 14);
        Map<String, Object> snap = snapshot == null ? Map.of()
                : json.readValue(snapshot, new TypeReference<LinkedHashMap<String, Object>>() { });
        return new CaseRow(rs.getLong(base + 1), TargetType.valueOf(rs.getString(base + 2)), rs.getString(base + 3),
                nullableLong(rs, base + 4), nullableLong(rs, base + 5), CaseStatus.valueOf(rs.getString(base + 6)),
                rs.getInt(base + 7), ReportReason.valueOf(rs.getString(base + 8)), rs.getInt(base + 9),
                rs.getBoolean(base + 10), rs.getString(base + 11), rs.getString(base + 12), instant(rs, base + 13),
                snap, instant(rs, base + 15), instant(rs, base + 16), instant(rs, base + 17), instant(rs, base + 18));
    }

    private ReportRow reportRow(ResultSet rs, int i) throws SQLException {
        return new ReportRow(rs.getLong(1), rs.getLong(2), ReportSource.valueOf(rs.getString(3)), nullableLong(rs, 4),
                rs.getString(5), rs.getString(6), ReportReason.valueOf(rs.getString(7)), rs.getString(8),
                rs.getBoolean(9), rs.getBoolean(10), rs.getString(12), instant(rs, 13), instant(rs, 14));
    }

    private DecisionRow decisionRow(ResultSet rs, int i) throws SQLException {
        String policy = rs.getString("policy_reason");
        return new DecisionRow(rs.getLong("id"), rs.getLong("case_id"), rs.getString("stage"),
                ModerationAction.valueOf(rs.getString("action")), policy == null ? null : ReportReason.valueOf(policy),
                rs.getString("statement"), rs.getBoolean("automated"), nullableLong(rs, "moderator_id"),
                instant(rs, "created_at"));
    }

    private AppealRow appealRow(ResultSet rs, int i) throws SQLException {
        return new AppealRow(rs.getLong("id"), rs.getLong("case_id"), rs.getLong("decision_id"),
                nullableLong(rs, "appellant_id"), rs.getString("appellant_role"), rs.getString("reason"),
                rs.getString("status"), nullableLong(rs, "result_decision_id"), instant(rs, "created_at"),
                instant(rs, "resolved_at"));
    }

    private static Long nullableLong(ResultSet rs, int column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long v = rs.getLong(column);
        return rs.wasNull() ? null : v;
    }

    private static Instant instant(ResultSet rs, int column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
