package com.friendlyeats.application.privacy;

import com.friendlyeats.application.account.AccountExceptions;
import com.friendlyeats.application.account.AccountNotifier;
import com.friendlyeats.application.media.MediaStorage;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UuidV7;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Exportar meus dados (RF18; LGPD, art. 18, V, e art. 19, II e §3º).
 *
 * <ul>
 *   <li>Pedido com o modo sudo (o controller chama o {@code StepUp}), no máximo um por dia; o prazo de atendimento
 *       de 15 dias fica gravado ({@code due_at}) e um aviso vai por e-mail, para o caso de sessão sequestrada.</li>
 *   <li>O job ({@link DataExportJob}) gera o ZIP ({@link DataExportBuilder}), grava em {@code exports/…} como objeto
 *       privado e avisa por e-mail. O link de download é uma URL pré-assinada que vale até {@code ready + 72 h}.</li>
 *   <li>Estados: REQUESTED → PROCESSING → READY → EXPIRED (o ZIP sai do bucket); FAILED depois de 3 tentativas.</li>
 * </ul>
 */
@Service
public class DataExportService {

    private static final Logger log = LoggerFactory.getLogger(DataExportService.class);
    static final int MAX_ATTEMPTS = 3;
    static final Duration STALE = Duration.ofMinutes(30);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final DataExportBuilder builder;
    private final MediaStorage storage;
    private final UserAccountRepository users;
    private final AccountNotifier notifier;
    private final Clock clock;
    private final Duration due;
    private final Duration linkTtl;

    public DataExportService(JdbcTemplate jdbc, TransactionTemplate tx, DataExportBuilder builder, MediaStorage storage,
                             UserAccountRepository users, AccountNotifier notifier, Clock clock,
                             @Value("${friendly-eats.privacy.export-due:15d}") Duration due,
                             @Value("${friendly-eats.privacy.export-link-ttl:72h}") Duration linkTtl) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.builder = builder;
        this.storage = storage;
        this.users = users;
        this.notifier = notifier;
        this.clock = clock;
        this.due = due;
        this.linkTtl = linkTtl;
    }

    public enum Status { REQUESTED, PROCESSING, READY, EXPIRED, FAILED }

    /** {@code downloadUrl} só com o status READY e dentro da validade. */
    public record ExportView(UUID id, Status status, String statusLabel, Instant requestedAt, Instant dueAt,
                             Instant readyAt, Instant expiresAt, Long sizeBytes, String downloadUrl) {
    }

    private record Row(UUID id, long userId, Status status, Instant requestedAt, Instant dueAt, Instant readyAt,
                       Instant expiresAt, String objectKey, Long sizeBytes, int attempts) {
    }

    /** Novo pedido (sudo já conferido): um por dia, contando os que não falharam. */
    public ExportView request(UserAccount user) {
        return tx.execute(s -> {
            users.lockById(user.getId()).orElseThrow();
            Instant now = clock.instant();
            List<Timestamp> recent = jdbc.queryForList("""
                    SELECT requested_at FROM data_export
                    WHERE user_id = ? AND status <> 'FAILED' AND requested_at > ? ORDER BY requested_at DESC
                    """, Timestamp.class, user.getId(), Timestamp.from(now.minus(Duration.ofDays(1))));
            if (!recent.isEmpty()) {
                throw new AccountExceptions.TooManyAttempts(
                        Duration.between(now, recent.getFirst().toInstant().plus(Duration.ofDays(1))));
            }
            UUID id = UuidV7.next();
            jdbc.update("INSERT INTO data_export (id, user_id, requested_at, due_at) VALUES (?, ?, ?, ?)",
                    id, user.getId(), Timestamp.from(now), Timestamp.from(now.plus(due)));
            notifier.dataExportRequested(user, now.plus(due));
            return view(find(id));
        });
    }

    public List<ExportView> list(long userId) {
        return jdbc.query("SELECT * FROM data_export WHERE user_id = ? ORDER BY requested_at DESC LIMIT 10",
                this::row, userId).stream().map(this::view).toList();
    }

    /** O job: pedidos na fila (e os presos em PROCESSING há mais de 30 min), cada um na própria transação. */
    public int processPending() {
        Instant now = clock.instant();
        List<UUID> queue = jdbc.queryForList("""
                SELECT id FROM data_export
                WHERE status = 'REQUESTED' OR (status = 'PROCESSING' AND started_at < ?)
                ORDER BY requested_at LIMIT 10
                """, UUID.class, Timestamp.from(now.minus(STALE)));
        int done = 0;
        for (UUID id : queue) {
            if (process(id)) {
                done++;
            }
        }
        return done;
    }

    /** Links vencidos: o ZIP sai do bucket e o pedido vira EXPIRED. */
    public int expire() {
        List<Row> due = jdbc.query("SELECT * FROM data_export WHERE status = 'READY' AND expires_at < ?", this::row,
                Timestamp.from(clock.instant()));
        for (Row r : due) {
            if (r.objectKey() != null) {
                storage.delete(r.objectKey());
            }
            jdbc.update("UPDATE data_export SET status = 'EXPIRED', object_key = NULL WHERE id = ?", r.id());
        }
        return due.size();
    }

    /**
     * Conta eliminada (RF5): os pedidos saem do banco; devolve as chaves dos ZIPs, que quem chama apaga do bucket
     * depois do commit.
     */
    public List<String> eraseFor(long userId) {
        List<String> keys = jdbc.queryForList(
                "SELECT object_key FROM data_export WHERE user_id = ? AND object_key IS NOT NULL", String.class, userId);
        jdbc.update("DELETE FROM data_export WHERE user_id = ?", userId);
        return keys;
    }

    private boolean process(UUID id) {
        Row claimed = tx.execute(s -> {
            List<Row> rows = jdbc.query("""
                    SELECT * FROM data_export WHERE id = ? AND status IN ('REQUESTED','PROCESSING') FOR UPDATE SKIP LOCKED
                    """, this::row, id);
            if (rows.isEmpty()) {
                return null;
            }
            jdbc.update("UPDATE data_export SET status = 'PROCESSING', started_at = ?, attempts = attempts + 1 WHERE id = ?",
                    Timestamp.from(clock.instant()), id);
            return rows.getFirst();
        });
        if (claimed == null) {
            return false;
        }
        try {
            Instant now = clock.instant();
            byte[] zip = builder.zip(claimed.userId(), now);
            String key = "exports/" + claimed.userId() + "/" + id + ".zip";
            storage.writePrivate(key, zip, "application/zip", "friendly-eats-dados-" + now.toString().substring(0, 10) + ".zip");
            Instant expires = now.plus(linkTtl);
            tx.executeWithoutResult(s -> jdbc.update("""
                    UPDATE data_export SET status = 'READY', ready_at = ?, expires_at = ?, object_key = ?, size_bytes = ?,
                                           error = NULL
                    WHERE id = ?
                    """, Timestamp.from(now), Timestamp.from(expires), key, (long) zip.length, id));
            users.findById(claimed.userId()).ifPresent(u -> notifier.dataExportReady(u, expires));
            return true;
        } catch (RuntimeException e) {
            log.error("Exportação {} falhou", id, e);
            boolean last = claimed.attempts() + 1 >= MAX_ATTEMPTS;
            tx.executeWithoutResult(s -> jdbc.update("UPDATE data_export SET status = ?, error = ? WHERE id = ?",
                    last ? "FAILED" : "REQUESTED", "Falha ao gerar o arquivo; nova tentativa automática.", id));
            return false;
        }
    }

    private Row find(UUID id) {
        return jdbc.query("SELECT * FROM data_export WHERE id = ?", this::row, id).getFirst();
    }

    private ExportView view(Row r) {
        Instant now = clock.instant();
        Status status = r.status() == Status.READY && r.expiresAt() != null && !r.expiresAt().isAfter(now)
                ? Status.EXPIRED : r.status();
        String url = status == Status.READY && r.objectKey() != null
                ? storage.presignGet(r.objectKey(), Duration.between(now, r.expiresAt())) : null;
        return new ExportView(r.id(), status, label(status), r.requestedAt(), r.dueAt(), r.readyAt(), r.expiresAt(),
                r.sizeBytes(), url);
    }

    static String label(Status s) {
        return switch (s) {
            case REQUESTED -> "Pedido recebido";
            case PROCESSING -> "Preparando o arquivo";
            case READY -> "Pronto para baixar";
            case EXPIRED -> "Link expirado";
            case FAILED -> "Não foi possível gerar";
        };
    }

    private Row row(ResultSet rs, int i) throws SQLException {
        long size = rs.getLong("size_bytes");
        Long sizeBytes = rs.wasNull() ? null : size;
        return new Row(rs.getObject("id", UUID.class), rs.getLong("user_id"), Status.valueOf(rs.getString("status")),
                instant(rs.getTimestamp("requested_at")), instant(rs.getTimestamp("due_at")),
                instant(rs.getTimestamp("ready_at")), instant(rs.getTimestamp("expires_at")),
                rs.getString("object_key"), sizeBytes, rs.getInt("attempts"));
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
