package com.friendlyeats.application.privacy;

import com.friendlyeats.application.account.AccountExceptions;
import com.friendlyeats.application.common.CommonExceptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Registro de incidentes de segurança (RF18; LGPD, art. 48; Resolução CD/ANPD 15/2024). Todo incidente entra, mesmo
 * os que não exigem comunicação; o registro fica 5 anos. O relógio de 3 dias úteis começa quando se sabe que o
 * incidente atingiu dado pessoal ({@code personalDataAwareAt}). Sem tela: o runbook está em docs/legal/INCIDENTES.md.
 */
@Service
public class SecurityIncidentService {

    static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    static final int BUSINESS_DAYS = 3;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final Clock clock;
    private final Duration retention;

    public SecurityIncidentService(JdbcTemplate jdbc, JsonMapper json, Clock clock,
                                   @Value("${friendly-eats.privacy.incident-retention:1826d}") Duration retention) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
        this.retention = retention;
    }

    public enum Decision { PENDING, NOTIFY, NOT_REQUIRED }

    public record IncidentDraft(String title, String description, Instant discoveredAt, Instant personalDataAwareAt,
                                List<String> dataCategories, Integer affectedSubjects, String measures,
                                String recordedBy) {
    }

    public record Incident(long id, String title, String description, Instant discoveredAt,
                           Instant personalDataAwareAt, Instant communicationDeadline, List<String> dataCategories,
                           Integer affectedSubjects, String measures, Boolean riskRelevant, Decision decision,
                           String decisionReason, Instant anpdNotifiedAt, Instant subjectsNotifiedAt,
                           String recordedBy, Instant createdAt, Instant retainUntil) {
    }

    /**
     * Fim do prazo de comunicação: o fim do 3º dia útil (seg. a sex., horário de Brasília) depois de
     * {@code aware}. ⚖️ Feriados não entram na conta: o prazo fica mais curto, nunca mais longo.
     */
    public static Instant communicationDeadline(Instant aware) {
        LocalDate day = aware.atZone(BRAZIL).toLocalDate();
        int counted = 0;
        while (counted < BUSINESS_DAYS) {
            day = day.plusDays(1);
            if (day.getDayOfWeek() != DayOfWeek.SATURDAY && day.getDayOfWeek() != DayOfWeek.SUNDAY) {
                counted++;
            }
        }
        return day.atTime(LocalTime.of(23, 59, 59)).atZone(BRAZIL).toInstant();
    }

    @Transactional
    public Incident register(IncidentDraft d) {
        List<String> errors = new ArrayList<>();
        if (d.title() == null || d.title().isBlank() || d.title().length() > 200) {
            errors.add("Dê um título de até 200 caracteres.");
        }
        if (d.description() == null || d.description().isBlank() || d.description().length() > 4000) {
            errors.add("Descreva o incidente (até 4000 caracteres).");
        }
        if (d.discoveredAt() == null) {
            errors.add("Informe quando o incidente foi descoberto.");
        }
        if (d.recordedBy() == null || d.recordedBy().isBlank()) {
            errors.add("Informe quem registra.");
        }
        if (!errors.isEmpty()) {
            throw new AccountExceptions.Invalid(errors);
        }
        Instant now = clock.instant();
        Long id = jdbc.queryForObject("""
                INSERT INTO security_incident (title, description, discovered_at, personal_data_aware_at,
                    communication_deadline, data_categories, affected_subjects, measures, recorded_by, created_at,
                    retain_until)
                VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, d.title().strip(), d.description().strip(), ts(d.discoveredAt()),
                ts(d.personalDataAwareAt()),
                d.personalDataAwareAt() == null ? null : ts(communicationDeadline(d.personalDataAwareAt())),
                json.writeValueAsString(d.dataCategories() == null ? List.of() : d.dataCategories()),
                d.affectedSubjects(), d.measures(), d.recordedBy().strip(), ts(now), ts(now.plus(retention)));
        return get(id);
    }

    /** Soube depois que atingiu dado pessoal: o relógio dos 3 dias úteis começa agora. */
    @Transactional
    public Incident personalDataAware(long id, Instant aware) {
        jdbc.update("""
                UPDATE security_incident SET personal_data_aware_at = ?, communication_deadline = ? WHERE id = ?
                """, ts(aware), ts(communicationDeadline(aware)), id);
        return get(id);
    }

    /** Decide se comunica (risco ou dano relevante) e por quê; a decisão de não comunicar também fica registrada. */
    @Transactional
    public Incident decide(long id, boolean riskRelevant, boolean notify, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AccountExceptions.Invalid("Registre o motivo da decisão.");
        }
        jdbc.update("""
                UPDATE security_incident SET risk_relevant = ?, decision = ?, decision_reason = ? WHERE id = ?
                """, riskRelevant, notify ? Decision.NOTIFY.name() : Decision.NOT_REQUIRED.name(), reason.strip(), id);
        return get(id);
    }

    @Transactional
    public Incident notified(long id, Instant anpdAt, Instant subjectsAt) {
        jdbc.update("""
                UPDATE security_incident SET anpd_notified_at = COALESCE(?, anpd_notified_at),
                                             subjects_notified_at = COALESCE(?, subjects_notified_at)
                WHERE id = ?
                """, ts(anpdAt), ts(subjectsAt), id);
        return get(id);
    }

    @Transactional(readOnly = true)
    public Incident get(long id) {
        return jdbc.query("SELECT * FROM security_incident WHERE id = ?", this::row, id).stream().findFirst()
                .orElseThrow(() -> new CommonExceptions.NotFound("Incidente não encontrado."));
    }

    @Transactional(readOnly = true)
    public List<Incident> list() {
        return jdbc.query("SELECT * FROM security_incident ORDER BY discovered_at DESC, id DESC", this::row);
    }

    /** Sem decisão e com o prazo de comunicação vencido: o job avisa. */
    @Transactional(readOnly = true)
    public List<Incident> overdue() {
        return jdbc.query("""
                SELECT * FROM security_incident WHERE decision = 'PENDING' AND communication_deadline < ?
                ORDER BY communication_deadline
                """, this::row, ts(clock.instant()));
    }

    /** Apaga o que passou dos 5 anos de guarda. */
    @Transactional
    public int purgeExpired() {
        return jdbc.update("DELETE FROM security_incident WHERE retain_until < ?", ts(clock.instant()));
    }

    private Incident row(ResultSet rs, int i) throws SQLException {
        int affected = rs.getInt("affected_subjects");
        Integer affectedSubjects = rs.wasNull() ? null : affected;
        boolean risk = rs.getBoolean("risk_relevant");
        Boolean riskRelevant = rs.wasNull() ? null : risk;
        List<String> categories = List.of(json.readValue(rs.getString("data_categories"), String[].class));
        return new Incident(rs.getLong("id"), rs.getString("title"), rs.getString("description"),
                instant(rs.getTimestamp("discovered_at")), instant(rs.getTimestamp("personal_data_aware_at")),
                instant(rs.getTimestamp("communication_deadline")), categories, affectedSubjects,
                rs.getString("measures"), riskRelevant, Decision.valueOf(rs.getString("decision")),
                rs.getString("decision_reason"), instant(rs.getTimestamp("anpd_notified_at")),
                instant(rs.getTimestamp("subjects_notified_at")), rs.getString("recorded_by"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("retain_until")));
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
