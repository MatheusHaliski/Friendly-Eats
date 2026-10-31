package com.friendlyeats.application.guardian;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Leitura do consentimento do responsável para o dado sensível do menor (LGPD, art. 14, §1º; RF17). Separado do
 * {@link GuardianService} para o {@code ConsentService} (RF3) consultar sem depender dele.
 */
@Component
public class GuardianConsents {

    private final JdbcTemplate jdbc;

    public GuardianConsents(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** O responsável vinculado (vínculo ativo) consentiu o dado sensível do perfil. */
    public boolean sensitiveDataConsented(long minorId) {
        Boolean found = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM guardian_link
                               WHERE minor_id = ? AND status = 'ACTIVE' AND sensitive_consent_at IS NOT NULL)
                """, Boolean.class, minorId);
        return Boolean.TRUE.equals(found);
    }
}
