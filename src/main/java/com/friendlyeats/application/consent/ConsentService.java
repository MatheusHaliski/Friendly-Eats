package com.friendlyeats.application.consent;

import com.friendlyeats.application.account.AccountExceptions;
import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.account.LegalDocuments;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.guardian.GuardianConsents;
import com.friendlyeats.domain.model.ConsentPurpose;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.security.ClientInfo;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * RF3 · consentimentos (LGPD Art. 7º, I · Art. 8º, §5º · Art. 11). Ligar e desligar é igualmente fácil; cada mudança
 * gera uma linha nova em {@code consent_event} (append-only) e o estado atual vem de {@code user_consent_current}.
 * Desligar tem efeito imediato na mesma transação: SENSITIVE_PROFILE_DATA apaga os estilos alimentares e
 * LOCATION_TRACKING apaga a última localização salva. Para o menor de 18, SENSITIVE_PROFILE_DATA destrava com o
 * consentimento do responsável vinculado ({@link GuardianConsents}, RF17).
 */
@Service
public class ConsentService {

    private final UserAccountRepository users;
    private final JdbcTemplate jdbc;
    private final LegalDocuments legal;
    private final AgePolicy age;
    private final GuardianConsents guardians;

    public ConsentService(UserAccountRepository users, JdbcTemplate jdbc, LegalDocuments legal, AgePolicy age,
                          GuardianConsents guardians) {
        this.users = users;
        this.jdbc = jdbc;
        this.legal = legal;
        this.age = age;
        this.guardians = guardians;
    }

    public record ConsentView(ConsentPurpose purpose, String label, String description, String legalBasis,
                              boolean sensitive, boolean granted, Instant updatedAt, boolean lockedForMinor) {
    }

    public record ConsentList(List<ConsentView> items) {
    }

    private record Current(boolean granted, Instant at) {
    }

    @Transactional(readOnly = true)
    public ConsentList list(long userId) {
        UserAccount u = users.findById(userId).orElseThrow();
        Map<ConsentPurpose, Current> current = current(userId);
        List<ConsentView> items = new ArrayList<>();
        for (ConsentPurpose p : ConsentPurpose.values()) {
            items.add(view(p, current.get(p), u));
        }
        return new ConsentList(items);
    }

    /** Estado atual de uma finalidade; sem evento gravado vale o padrão, desligado. */
    @Transactional(readOnly = true)
    public boolean granted(long userId, ConsentPurpose purpose) {
        Current c = current(userId).get(purpose);
        return c != null && c.granted();
    }

    @Transactional
    public ConsentView set(long userId, String purposeCode, Boolean granted, ClientInfo client) {
        ConsentPurpose purpose = ConsentPurpose.fromCode(purposeCode)
                .orElseThrow(() -> new CommonExceptions.NotFound("Finalidade de consentimento desconhecida."));
        if (granted == null) {
            throw new AccountExceptions.Invalid("Informe granted: true para ligar ou false para desligar.");
        }
        // trava a linha da conta: serializa com o PATCH do perfil, que confere este consentimento antes de gravar
        UserAccount u = users.lockById(userId).orElseThrow();
        if (granted && lockedForMinor(purpose, u)) {
            throw new AccountExceptions.Forbidden(purpose.getMinorRule() == ConsentPurpose.MinorRule.UNDER_18
                    ? "Para menores de 18 anos, \"" + purpose.getLabel() + "\" fica desligado até o responsável "
                    + "consentir (LGPD Art. 14)."
                    : "Para menores de 16 anos, \"" + purpose.getLabel() + "\" fica desligado (ECA Digital).");
        }
        Current before = current(userId).get(purpose);
        if (before == null || before.granted() != granted) {
            jdbc.update("""
                    INSERT INTO consent_event (user_id, purpose, legal_basis, granted, policy_version, channel, ip_hash)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """, userId, purpose.name(), purpose.getLegalBasis(), granted, legal.privacyVersion(),
                    client.channel(), client.ipHash());
        }
        if (!granted) {
            revokeEffects(purpose, u);
        }
        return view(purpose, current(userId).get(purpose), u);
    }

    /** Grava as finalidades desligadas no cadastro (RF1), na mesma transação da conta. */
    public void recordDefaults(long userId, ClientInfo client) {
        for (ConsentPurpose p : ConsentPurpose.values()) {
            jdbc.update("""
                    INSERT INTO consent_event (user_id, purpose, legal_basis, granted, policy_version, channel, ip_hash)
                    VALUES (?, ?, ?, FALSE, ?, ?, ?)
                    """, userId, p.name(), p.getLegalBasis(), legal.privacyVersion(), client.channel(), client.ipHash());
        }
    }

    private void revokeEffects(ConsentPurpose purpose, UserAccount u) {
        switch (purpose) {
            case SENSITIVE_PROFILE_DATA -> {
                if (u.getDietaryStyles() != null && !u.getDietaryStyles().isEmpty()) {
                    u.setDietaryStyles(new ArrayList<>());
                    users.save(u);
                }
            }
            case LOCATION_TRACKING -> {
                if (u.getLastSavedLocation() != null) {
                    u.setLastSavedLocation(null);
                    users.save(u);
                }
            }
            default -> {
                // PERSONALIZED_FEED, MARKETING e THIRD_PARTY_SHARING: quem consome confere o estado atual a cada uso
            }
        }
    }

    private Map<ConsentPurpose, Current> current(long userId) {
        Map<ConsentPurpose, Current> out = new EnumMap<>(ConsentPurpose.class);
        jdbc.query("SELECT purpose, granted, occurred_at FROM user_consent_current WHERE user_id = ?", rs -> {
            Timestamp at = rs.getTimestamp("occurred_at");
            Current c = new Current(rs.getBoolean("granted"), at == null ? null : at.toInstant());
            ConsentPurpose.fromCode(rs.getString("purpose")).ifPresent(p -> out.put(p, c));
        }, userId);
        return out;
    }

    private boolean lockedForMinor(ConsentPurpose p, UserAccount u) {
        return switch (p.getMinorRule()) {
            case NONE -> false;
            case UNDER_16 -> age.protectedMinor(u.getUserDateOfBirth());
            // com o consentimento do responsável vinculado (RF17), o menor de 18 pode ligar (LGPD Art. 14, §1º)
            case UNDER_18 -> !age.adult(u.getUserDateOfBirth()) && !guardians.sensitiveDataConsented(u.getId());
        };
    }

    private ConsentView view(ConsentPurpose p, Current c, UserAccount u) {
        return new ConsentView(p, p.getLabel(), p.getDescription(), p.getLegalBasis(), p.isSensitive(),
                c != null && c.granted(), c == null ? null : c.at(), lockedForMinor(p, u));
    }
}
