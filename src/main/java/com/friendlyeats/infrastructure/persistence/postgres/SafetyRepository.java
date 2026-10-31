package com.friendlyeats.infrastructure.persistence.postgres;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Bloqueios, silenciados e palavras filtradas (V5 · RF19). Bloquear e silenciar são idempotentes; as consultas de
 * leitura servem às regras de quem vê o quê (feed, post, comentários, perfil e busca de perfis).
 */
@Repository
public class SafetyRepository {

    /** Pessoa relacionada e desde quando. */
    public record Related(long userId, Instant since) {
    }

    private final JdbcTemplate jdbc;

    public SafetyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------ bloquear

    public boolean block(long blocker, long blocked) {
        return jdbc.update("INSERT INTO user_block (blocker_id, blocked_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                blocker, blocked) == 1;
    }

    public boolean unblock(long blocker, long blocked) {
        return jdbc.update("DELETE FROM user_block WHERE blocker_id = ? AND blocked_id = ?", blocker, blocked) == 1;
    }

    public boolean blocks(long blocker, long blocked) {
        return exists("SELECT EXISTS (SELECT 1 FROM user_block WHERE blocker_id = ? AND blocked_id = ?)", blocker, blocked);
    }

    /** Um bloqueou o outro, em qualquer sentido. */
    public boolean blockedEitherWay(long a, long b) {
        return exists("""
                SELECT EXISTS (SELECT 1 FROM user_block
                               WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?))
                """, a, b, b, a);
    }

    public List<Related> blockedBy(long blocker) {
        return jdbc.query("SELECT blocked_id, created_at FROM user_block WHERE blocker_id = ? ORDER BY created_at DESC",
                (rs, i) -> new Related(rs.getLong(1), rs.getTimestamp(2).toInstant()), blocker);
    }

    // ------------------------------------------------------------ silenciar

    public boolean mute(long muter, long muted) {
        return jdbc.update("INSERT INTO user_mute (muter_id, muted_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                muter, muted) == 1;
    }

    public boolean unmute(long muter, long muted) {
        return jdbc.update("DELETE FROM user_mute WHERE muter_id = ? AND muted_id = ?", muter, muted) == 1;
    }

    public boolean mutes(long muter, long muted) {
        return exists("SELECT EXISTS (SELECT 1 FROM user_mute WHERE muter_id = ? AND muted_id = ?)", muter, muted);
    }

    public List<Related> mutedBy(long muter) {
        return jdbc.query("SELECT muted_id, created_at FROM user_mute WHERE muter_id = ? ORDER BY created_at DESC",
                (rs, i) -> new Related(rs.getLong(1), rs.getTimestamp(2).toInstant()), muter);
    }

    /** Quem {@code viewer} não deve ver: bloqueados nos dois sentidos e silenciados por ele. */
    public Set<Long> hiddenFrom(long viewer) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT blocked_id FROM user_block WHERE blocker_id = ?
                UNION SELECT blocker_id FROM user_block WHERE blocked_id = ?
                UNION SELECT muted_id FROM user_mute WHERE muter_id = ?
                """, Long.class, viewer, viewer, viewer));
    }

    // ------------------------------------------------------------ palavras filtradas

    public List<String> words(long userId) {
        return jdbc.queryForList("SELECT word FROM user_filtered_word WHERE user_id = ? ORDER BY word", String.class, userId);
    }

    public void replaceWords(long userId, Collection<String> words) {
        jdbc.update("DELETE FROM user_filtered_word WHERE user_id = ?", userId);
        jdbc.batchUpdate("INSERT INTO user_filtered_word (user_id, word) VALUES (?, ?) ON CONFLICT DO NOTHING",
                words.stream().map(w -> new Object[]{userId, w}).toList());
    }

    /** Filtro padrão gravado; vazio = vale o padrão pela idade. */
    public Optional<Boolean> defaultFilter(long userId) {
        return jdbc.queryForList("SELECT default_word_filter FROM user_safety_setting WHERE user_id = ?", Boolean.class,
                userId).stream().findFirst();
    }

    public void setDefaultFilter(long userId, boolean enabled) {
        jdbc.update("""
                INSERT INTO user_safety_setting (user_id, default_word_filter, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET default_word_filter = EXCLUDED.default_word_filter,
                                                    updated_at = EXCLUDED.updated_at
                """, userId, enabled, Timestamp.from(Instant.now()));
    }

    /** Eliminação da conta (RF5): some tudo o que a pessoa configurou e todo bloqueio em que ela aparece. */
    public void deleteAllOf(long userId) {
        jdbc.update("DELETE FROM user_block WHERE blocker_id = ? OR blocked_id = ?", userId, userId);
        jdbc.update("DELETE FROM user_mute WHERE muter_id = ? OR muted_id = ?", userId, userId);
        jdbc.update("DELETE FROM user_filtered_word WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_safety_setting WHERE user_id = ?", userId);
    }

    private boolean exists(String sql, Object... args) {
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, Boolean.class, args));
    }
}
