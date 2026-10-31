package com.friendlyeats.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Idade verificada para os testes (RF17): conteúdo de bebida só aparece para maior de 18 com a verificação feita.
 * Grava o mesmo que o fluxo real deixa no banco ao concluir no fornecedor simulado.
 */
public final class AgeFixtures {

    private AgeFixtures() {
    }

    public static void verifiedAdult(JdbcTemplate jdbc, long userId) {
        jdbc.update("""
                INSERT INTO age_verification (user_id, status, method, provider, provider_ref, verified_at)
                VALUES (?, 'VERIFIED_ADULT', 'SIMULADO', 'fake-dev', 'fake-teste', now())
                ON CONFLICT (user_id) DO UPDATE SET status = 'VERIFIED_ADULT', verified_at = now()
                """, userId);
    }
}
