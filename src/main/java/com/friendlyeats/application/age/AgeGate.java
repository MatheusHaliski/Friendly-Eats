package com.friendlyeats.application.age;

import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.domain.model.UserAccount;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Quem vê conteúdo de bebida alcoólica (RF17; ECA Digital, art. 9º, §1º; Decreto 12.880/2026, arts. 15 e 19):
 * só a conta com a idade <b>verificada</b> como maior de 18, dentro da validade. A data de nascimento declarada só
 * restringe: quem declarou menos de 18 não vê, mesmo verificado. Visitante e conta sem verificação recebem a
 * versão sem esse conteúdo (posts {@code AGE_18} e lugares com foco em bebida).
 */
@Component
public class AgeGate {

    static final String ANONYMOUS = "Entre na sua conta e verifique a idade para ver: conteúdo de bebida alcoólica é "
            + "só para maiores de 18.";

    /** Resultado da regra para quem está logado. */
    public enum Access {
        /** Maior de 18 verificado. */
        ALLOWED,
        /** Maior pela data declarada, mas sem verificação válida: pode verificar. */
        VERIFY,
        /** Menor pela data declarada ou pela verificação: não vê e a página não revela o conteúdo. */
        DENIED
    }

    /** Linha de {@code age_verification}. */
    public record Current(AgeStatus status, String method, String provider, String providerRef,
                          Instant pendingExpiresAt, Instant verifiedAt, Instant updatedAt) {
    }

    private final JdbcTemplate jdbc;
    private final AgePolicy age;
    private final Clock clock;
    private final Duration validity;

    public AgeGate(JdbcTemplate jdbc, AgePolicy age, Clock clock,
                   @Value("${friendly-eats.age-verification.validity:365d}") Duration validity) {
        this.jdbc = jdbc;
        this.age = age;
        this.clock = clock;
        this.validity = validity;
    }

    public Optional<Current> current(long userId) {
        List<Current> rows = jdbc.query("""
                SELECT status, method, provider, provider_ref, pending_expires_at, verified_at, updated_at
                FROM age_verification WHERE user_id = ?
                """, (rs, i) -> new Current(AgeStatus.valueOf(rs.getString("status")), rs.getString("method"),
                rs.getString("provider"), rs.getString("provider_ref"), instant(rs.getTimestamp("pending_expires_at")),
                instant(rs.getTimestamp("verified_at")), instant(rs.getTimestamp("updated_at"))), userId);
        return rows.stream().findFirst();
    }

    /**
     * Estado que vale agora: sessão aberta que expirou e verificação além da validade voltam a {@code UNVERIFIED}
     * (⚖️ a validade de 365 dias é uma leitura do "a cada acesso" do art. 9º, §1º, a confirmar).
     */
    public AgeStatus effective(Optional<Current> c) {
        if (c.isEmpty()) {
            return AgeStatus.UNVERIFIED;
        }
        Current cur = c.get();
        Instant now = clock.instant();
        return switch (cur.status()) {
            case PENDING -> cur.pendingExpiresAt() != null && cur.pendingExpiresAt().isAfter(now)
                    ? AgeStatus.PENDING : AgeStatus.UNVERIFIED;
            case VERIFIED_ADULT, VERIFIED_MINOR -> validUntil(cur).filter(now::isBefore).isPresent()
                    ? cur.status() : AgeStatus.UNVERIFIED;
            default -> cur.status();
        };
    }

    public Optional<Instant> validUntil(Current c) {
        return Optional.ofNullable(c.verifiedAt()).map(v -> v.plus(validity));
    }

    public Access access(UserAccount user) {
        if (user == null || !age.adult(user.getUserDateOfBirth())) {
            return Access.DENIED;
        }
        return switch (effective(current(user.getId()))) {
            case VERIFIED_ADULT -> Access.ALLOWED;
            case VERIFIED_MINOR -> Access.DENIED;
            default -> Access.VERIFY;
        };
    }

    /** {@code true} só para maior de 18 verificado; visitante ({@code null}) nunca. */
    public boolean alcoholAllowed(UserAccount user) {
        return user != null && access(user) == Access.ALLOWED;
    }

    /**
     * Página de conteúdo de bebida (post 18+, lugar com foco em bebida): visitante → 401; conta de maior sem
     * verificação → 403 com {@code ageVerificationRequired}; menor → o mesmo 404 de "não encontrado".
     */
    public void requireAlcoholAccess(UserAccount viewer, Supplier<? extends RuntimeException> notFound) {
        if (viewer == null) {
            throw new CommonExceptions.Unauthenticated(ANONYMOUS);
        }
        switch (access(viewer)) {
            case ALLOWED -> {
            }
            case VERIFY -> throw new AgeExceptions.VerificationRequired();
            case DENIED -> throw notFound.get();
        }
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
