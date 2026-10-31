package com.friendlyeats.infrastructure.persistence.postgres;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/** Papéis da conta (tabela {@code user_role}, V5 · RF13). Hoje só {@code MODERATOR}. */
@Repository
public class UserRoleRepository {

    public static final String MODERATOR = "MODERATOR";

    private final JdbcTemplate jdbc;

    public UserRoleRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> rolesOf(long userId) {
        return jdbc.queryForList("SELECT role FROM user_role WHERE user_id = ? ORDER BY role", String.class, userId);
    }

    public boolean has(long userId, String role) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM user_role WHERE user_id = ? AND role = ?)", Boolean.class, userId, role));
    }

    /** Idempotente: conceder de novo não muda nada. */
    public boolean grant(long userId, String role, String grantedBy) {
        return jdbc.update("INSERT INTO user_role (user_id, role, granted_by) VALUES (?, ?, ?) ON CONFLICT DO NOTHING",
                userId, role, grantedBy) == 1;
    }

    public boolean revoke(long userId, String role) {
        return jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role = ?", userId, role) == 1;
    }
}
