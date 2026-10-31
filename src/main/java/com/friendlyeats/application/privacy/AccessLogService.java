package com.friendlyeats.application.privacy;

import com.friendlyeats.application.account.AccountExceptions;
import com.friendlyeats.security.AccessLogCipher;
import com.friendlyeats.security.AppSecrets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Registros de acesso a aplicações (RF18; Marco Civil, art. 15; Decreto 8.771/2016, art. 13).
 *
 * <ul>
 *   <li>Grava data e hora (UTC) e o IP com a porta lógica de origem, cifrados com AES-256-GCM
 *       ({@link AccessLogCipher}): o login sempre ({@code LOGIN}) e, para quem está logado, um {@code ACCESS} por
 *       usuário e IP a cada {@code access-log-every}. O IP continua só como HMAC em todo o resto do sistema.</li>
 *   <li>Guarda por 6 meses ({@code access-log-retention}) e apaga depois ({@link #purgeExpired}), salvo a guarda
 *       estendida a pedido de autoridade (art. 15, §2º).</li>
 *   <li>Não há endpoint: a consulta é um serviço interno ({@link #consult}) que exige motivo e o número do processo
 *       ou da ordem, e grava o inventário de quem consultou ({@code access_log_query}) na mesma transação.</li>
 * </ul>
 */
@Service
public class AccessLogService {

    private static final Logger log = LoggerFactory.getLogger(AccessLogService.class);

    private final JdbcTemplate jdbc;
    private final AccessLogCipher cipher;
    private final AppSecrets secrets;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Duration retention;
    private final Duration every;

    public AccessLogService(JdbcTemplate jdbc, AccessLogCipher cipher, AppSecrets secrets, StringRedisTemplate redis,
                            Clock clock,
                            @Value("${friendly-eats.privacy.access-log-retention:180d}") Duration retention,
                            @Value("${friendly-eats.privacy.access-log-every:1h}") Duration every) {
        this.jdbc = jdbc;
        this.cipher = cipher;
        this.secrets = secrets;
        this.redis = redis;
        this.clock = clock;
        this.retention = retention;
        this.every = every;
    }

    public enum Event { LOGIN, ACCESS }

    /** Uma linha do registro, já decifrada, como vai para a autoridade. */
    public record Entry(long id, Event event, Instant occurredAt, String ip, int port, String channel) {
    }

    /** Pedido de consulta: quem pede, por quê e com base em qual processo ou ordem judicial. */
    public record ConsultRequest(String requestedBy, String reason, String legalOrder, long userId, Instant from,
                                 Instant to) {
    }

    public record ConsultResult(long queryId, List<Entry> entries) {
    }

    /** Login concluído: sempre grava. Roda na própria transação, para o registro não se perder com um erro depois. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void login(long userId, String ip, int port, String channel) {
        insert(userId, Event.LOGIN, ip, port, channel);
        mark(userId, ip);
    }

    /** Pedido de quem está logado: grava no máximo um por usuário e IP a cada {@code every}. */
    public void access(long userId, String ip, int port, String channel) {
        try {
            Boolean first = redis.opsForValue().setIfAbsent(key(userId, ip), "1", every);
            if (Boolean.TRUE.equals(first)) {
                insert(userId, Event.ACCESS, ip, port, channel);
            }
        } catch (RuntimeException e) {
            // o registro não pode derrubar o pedido do usuário; a falha fica no log de operação
            log.warn("Registro de acesso não gravado para o usuário {}: {}", userId, e.getMessage());
        }
    }

    /**
     * Consulta para atender ordem judicial (Marco Civil, arts. 10, §1º, e 22): exige motivo e número do processo ou
     * da ordem, devolve os registros decifrados do período e grava quem consultou.
     */
    @Transactional
    public ConsultResult consult(ConsultRequest r) {
        List<String> errors = new ArrayList<>();
        if (r.requestedBy() == null || r.requestedBy().isBlank()) {
            errors.add("Informe quem está consultando.");
        }
        if (r.reason() == null || r.reason().strip().length() < 10) {
            errors.add("Informe o motivo da consulta (mínimo de 10 caracteres).");
        }
        if (r.legalOrder() == null || r.legalOrder().isBlank()) {
            errors.add("Informe o número do processo ou da ordem judicial.");
        }
        if (r.from() == null || r.to() == null || !r.from().isBefore(r.to())) {
            errors.add("Informe o período (início antes do fim).");
        }
        if (!errors.isEmpty()) {
            throw new AccountExceptions.Invalid(errors);
        }
        List<Entry> entries = jdbc.query("""
                SELECT id, event, occurred_at, ip_cipher, channel FROM access_log
                WHERE user_id = ? AND occurred_at >= ? AND occurred_at < ? ORDER BY occurred_at, id
                """, (rs, i) -> {
            String[] ipPort = cipher.open(rs.getBytes("ip_cipher"), r.userId());
            return new Entry(rs.getLong("id"), Event.valueOf(rs.getString("event")),
                    rs.getTimestamp("occurred_at").toInstant(), ipPort[0], Integer.parseInt(ipPort[1]),
                    rs.getString("channel"));
        }, r.userId(), Timestamp.from(r.from()), Timestamp.from(r.to()));
        Long id = jdbc.queryForObject("""
                INSERT INTO access_log_query (requested_by, reason, legal_order, target_user_id, period_from, period_to,
                                              rows_returned, queried_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, r.requestedBy().strip(), r.reason().strip(), r.legalOrder().strip(), r.userId(),
                Timestamp.from(r.from()), Timestamp.from(r.to()), entries.size(), Timestamp.from(clock.instant()));
        return new ConsultResult(id, entries);
    }

    /** Guarda estendida a pedido de autoridade (Marco Civil, art. 15, §2º): os registros do usuário ficam até {@code until}. */
    @Transactional
    public int hold(long userId, Instant until) {
        return jdbc.update("""
                UPDATE access_log SET legal_hold_until = GREATEST(COALESCE(legal_hold_until, ?), ?) WHERE user_id = ?
                """, Timestamp.from(until), Timestamp.from(until), userId);
    }

    /** Apaga o que passou dos 6 meses e não está em guarda estendida. */
    @Transactional
    public int purgeExpired() {
        Timestamp now = Timestamp.from(clock.instant());
        return jdbc.update("""
                DELETE FROM access_log
                WHERE occurred_at < ? AND (legal_hold_until IS NULL OR legal_hold_until < ?)
                """, Timestamp.from(clock.instant().minus(retention)), now);
    }

    private void insert(long userId, Event event, String ip, int port, String channel) {
        jdbc.update("""
                INSERT INTO access_log (user_id, event, occurred_at, ip_cipher, channel) VALUES (?, ?, ?, ?, ?)
                """, userId, event.name(), Timestamp.from(clock.instant()), cipher.seal(ip, port, userId), channel);
    }

    private void mark(long userId, String ip) {
        try {
            redis.opsForValue().set(key(userId, ip), "1", every);
        } catch (RuntimeException e) {
            log.warn("Marca de registro de acesso não gravada: {}", e.getMessage());
        }
    }

    private String key(long userId, String ip) {
        return "fe:access-log:" + userId + ":" + secrets.fingerprint("ip", ip).substring(0, 16);
    }
}
