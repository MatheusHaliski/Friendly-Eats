package com.friendlyeats.application.age;

import com.friendlyeats.application.account.AccountExceptions;
import com.friendlyeats.application.account.AccountNotifier;
import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.age.AgeGate.Current;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Verificação de idade (RF17): começar no fornecedor, concluir perguntando o resultado a ele, histórico
 * append-only e contestação revisada por uma pessoa (Decreto 12.880/2026, art. 27).
 *
 * <p>Guarda só status, método, fornecedor, id da verificação e datas: nunca imagem ou cópia de documento
 * (art. 24, §3º). A data de nascimento declarada continua valendo para as travas de menor e só restringe: quem
 * declarou menos de 18 nem começa a verificação.
 */
@Service
public class AgeVerificationService {

    static final int MAX_STARTS_PER_DAY = 5;

    private final JdbcTemplate jdbc;
    private final AgeGate gate;
    private final AgePolicy age;
    private final UserAccountRepository users;
    private final ObjectProvider<AgeVerificationProvider> providers;
    private final AccountNotifier notifier;
    private final Clock clock;
    private final String returnUrl;
    private final Set<String> reviewers;

    public AgeVerificationService(JdbcTemplate jdbc, AgeGate gate, AgePolicy age, UserAccountRepository users,
                                  ObjectProvider<AgeVerificationProvider> providers, AccountNotifier notifier,
                                  Clock clock, @Value("${friendly-eats.public-base-url}") String publicBaseUrl,
                                  @Value("${friendly-eats.age-verification.reviewers:}") String reviewers) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.age = age;
        this.users = users;
        this.providers = providers;
        this.notifier = notifier;
        this.clock = clock;
        this.returnUrl = publicBaseUrl.replaceAll("/+$", "") + "/configuracoes/idade";
        this.reviewers = Arrays.stream(reviewers.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    // ------------------------------------------------------------ views

    public record HistoryItem(AgeStatus status, String statusLabel, String method, String note, Instant occurredAt) {
    }

    public record AppealView(long id, AgeStatus contested, String reason, String status, Instant createdAt,
                             Instant decidedAt, String decisionNote) {
    }

    public record PendingView(String reference, Instant expiresAt) {
    }

    /**
     * {@code GET /api/v1/me/age-verification}. {@code alcoholContentVisible} decide o conteúdo de bebida;
     * {@code canStart}, {@code canAppeal} e {@code providerAvailable} montam os botões da tela.
     */
    public record AgeVerificationView(AgeStatus status, String statusLabel, String method, Instant verifiedAt,
                                      Instant validUntil, boolean alcoholContentVisible, boolean declaredAdult,
                                      boolean canStart, boolean canAppeal, boolean providerAvailable,
                                      boolean simulated, PendingView pending, AppealView appeal,
                                      List<HistoryItem> history) {
    }

    public record StartView(String reference, String redirectUrl, String instructions, Instant expiresAt,
                            boolean simulated) {
    }

    /** Item da fila de contestações, para quem revisa (sem data de nascimento nem documento). */
    public record QueueItem(long id, long userId, String handle, String name, AgeStatus contested, String reason,
                            Instant createdAt, List<HistoryItem> history) {
    }

    // ------------------------------------------------------------ titular

    @Transactional(readOnly = true)
    public AgeVerificationView view(long userId) {
        UserAccount user = users.findById(userId).orElseThrow();
        Optional<Current> current = gate.current(userId);
        AgeStatus status = gate.effective(current);
        boolean declaredAdult = age.adult(user.getUserDateOfBirth());
        AgeVerificationProvider provider = providers.getIfAvailable();
        AppealView appeal = latestAppeal(userId).orElse(null);
        boolean openAppeal = appeal != null && "OPEN".equals(appeal.status());
        boolean verified = status == AgeStatus.VERIFIED_ADULT || status == AgeStatus.VERIFIED_MINOR;
        return new AgeVerificationView(status, status.getLabel(),
                verified ? current.get().method() : null, verified ? current.get().verifiedAt() : null,
                verified ? gate.validUntil(current.get()).orElse(null) : null,
                status == AgeStatus.VERIFIED_ADULT && declaredAdult, declaredAdult,
                declaredAdult && provider != null && status != AgeStatus.VERIFIED_ADULT && !openAppeal,
                !openAppeal && (status == AgeStatus.VERIFIED_MINOR || status == AgeStatus.FAILED),
                provider != null, provider != null && provider.simulated(),
                status == AgeStatus.PENDING ? new PendingView(current.get().providerRef(),
                        current.get().pendingExpiresAt()) : null,
                appeal, history(userId));
    }

    @Transactional
    public StartView start(long userId) {
        UserAccount user = users.lockById(userId).orElseThrow();
        if (!age.adult(user.getUserDateOfBirth())) {
            throw new AccountExceptions.Forbidden("Pela data de nascimento informada, você tem menos de 18 anos: o "
                    + "conteúdo de bebida alcoólica fica oculto e não há o que verificar.");
        }
        AgeStatus status = gate.effective(gate.current(userId));
        if (status == AgeStatus.VERIFIED_ADULT) {
            throw new AccountExceptions.Conflict("Sua idade já está verificada.");
        }
        if (latestAppeal(userId).filter(a -> "OPEN".equals(a.status())).isPresent()) {
            throw new AccountExceptions.Conflict("Sua contestação está em análise. Aguarde a resposta por e-mail.");
        }
        AgeVerificationProvider provider = providers.getIfAvailable();
        if (provider == null) {
            throw new AgeExceptions.ProviderUnavailable();
        }
        Integer recent = jdbc.queryForObject("""
                SELECT count(*) FROM age_verification_event
                WHERE user_id = ? AND status = 'PENDING' AND occurred_at > ?
                """, Integer.class, userId, Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
        if (recent != null && recent >= MAX_STARTS_PER_DAY) {
            throw new AccountExceptions.TooManyAttempts(Duration.ofHours(1));
        }
        AgeVerificationProvider.Session session = provider.start(userId, returnUrl);
        save(userId, AgeStatus.PENDING, null, provider.id(), session.reference(), session.expiresAt(), null, null);
        return new StartView(session.reference(), session.redirectUrl(), session.instructions(), session.expiresAt(),
                provider.simulated());
    }

    /** Volta do fornecedor: o servidor pergunta o resultado; o navegador nunca o informa. */
    @Transactional
    public AgeVerificationView complete(long userId, String reference, Map<String, String> params) {
        users.lockById(userId).orElseThrow();
        Optional<Current> current = gate.current(userId);
        if (gate.effective(current) != AgeStatus.PENDING || reference == null
                || !reference.equals(current.get().providerRef())) {
            throw new AccountExceptions.Conflict("Essa verificação não está mais aberta. Comece de novo.");
        }
        AgeVerificationProvider provider = providers.getIfAvailable();
        if (provider == null || !provider.id().equals(current.get().provider())) {
            throw new AgeExceptions.ProviderUnavailable();
        }
        AgeVerificationProvider.Result result = provider.result(userId, reference, params == null ? Map.of() : params);
        Instant now = clock.instant();
        switch (result.outcome()) {
            case PENDING -> {
                return view(userId);
            }
            case ADULT -> save(userId, AgeStatus.VERIFIED_ADULT, result.method(), provider.id(), reference, null, now, null);
            case MINOR -> save(userId, AgeStatus.VERIFIED_MINOR, result.method(), provider.id(), reference, null, now, null);
            case FAILED -> save(userId, AgeStatus.FAILED, result.method(), provider.id(), reference, null, null, null);
        }
        return view(userId);
    }

    @Transactional
    public AppealView appeal(long userId, String reason) {
        users.lockById(userId).orElseThrow();
        AgeStatus status = gate.effective(gate.current(userId));
        if (status != AgeStatus.VERIFIED_MINOR && status != AgeStatus.FAILED) {
            throw new AccountExceptions.Conflict("Só dá para contestar quando a verificação falhou ou indicou menos "
                    + "de 18 anos.");
        }
        String text = reason == null ? "" : reason.strip();
        if (text.length() < 10 || text.length() > 1000) {
            throw new AccountExceptions.Invalid("Explique o motivo da contestação em 10 a 1000 caracteres.");
        }
        try {
            jdbc.update("INSERT INTO age_appeal (user_id, contested, reason, created_at) VALUES (?, ?, ?, ?)",
                    userId, status.name(), text, Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException e) {
            throw new AccountExceptions.Conflict("Você já tem uma contestação em análise.");
        }
        return latestAppeal(userId).orElseThrow();
    }

    // ------------------------------------------------------------ revisão humana (art. 27)

    public boolean isReviewer(long userId) {
        return users.findById(userId).map(u -> reviewers.contains(u.getEmail().toLowerCase(Locale.ROOT)))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<QueueItem> queue(long reviewerId) {
        requireReviewer(reviewerId);
        return jdbc.query("""
                SELECT a.id, a.user_id, u.handle, u.name, a.contested, a.reason, a.created_at
                FROM age_appeal a JOIN user_account u ON u.id = a.user_id
                WHERE a.status = 'OPEN' ORDER BY a.created_at, a.id
                """, (rs, i) -> new QueueItem(rs.getLong("id"), rs.getLong("user_id"), rs.getString("handle"),
                rs.getString("name"), AgeStatus.valueOf(rs.getString("contested")), rs.getString("reason"),
                rs.getTimestamp("created_at").toInstant(), history(rs.getLong("user_id"))));
    }

    /** Aceita: a verificação volta a {@code UNVERIFIED} e pode ser refeita por outro método. Recusada: fica como está. */
    @Transactional
    public AppealView decide(long reviewerId, long appealId, boolean accept, String note) {
        UserAccount reviewer = requireReviewer(reviewerId);
        List<Long> owners = jdbc.queryForList("SELECT user_id FROM age_appeal WHERE id = ? AND status = 'OPEN' FOR UPDATE",
                Long.class, appealId);
        if (owners.isEmpty()) {
            throw new CommonExceptions.NotFound("Contestação não encontrada ou já decidida.");
        }
        long userId = owners.getFirst();
        if (userId == reviewerId) {
            throw new AccountExceptions.Forbidden("Quem revisa não decide a própria contestação.");
        }
        String text = note == null || note.isBlank() ? null : note.strip();
        if (text != null && text.length() > 500) {
            throw new AccountExceptions.Invalid("A nota da decisão tem até 500 caracteres.");
        }
        jdbc.update("""
                UPDATE age_appeal SET status = ?, decided_at = ?, decided_by = ?, decision_note = ? WHERE id = ?
                """, accept ? "ACCEPTED" : "REJECTED", Timestamp.from(clock.instant()), reviewer.getEmail(), text,
                appealId);
        if (accept) {
            Current c = gate.current(userId).orElse(null);
            save(userId, AgeStatus.UNVERIFIED, null, c == null ? null : c.provider(), c == null ? null : c.providerRef(),
                    null, null, "Contestação aceita: refaça a verificação.");
        }
        UserAccount user = users.findById(userId).orElseThrow();
        notifier.ageAppealDecided(user, accept);
        return latestAppeal(userId).orElseThrow();
    }

    /** Conta eliminada (RF5): verificação, histórico e contestações saem por inteiro. */
    @Transactional
    public void eraseFor(long userId) {
        jdbc.update("DELETE FROM age_appeal WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM age_verification_event WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM age_verification WHERE user_id = ?", userId);
    }

    // ------------------------------------------------------------ partes

    @Transactional(readOnly = true)
    public List<HistoryItem> history(long userId) {
        return jdbc.query("""
                SELECT status, method, note, occurred_at FROM age_verification_event
                WHERE user_id = ? ORDER BY occurred_at DESC, id DESC LIMIT 50
                """, (rs, i) -> {
            AgeStatus s = AgeStatus.valueOf(rs.getString("status"));
            return new HistoryItem(s, s.getLabel(), rs.getString("method"), rs.getString("note"),
                    rs.getTimestamp("occurred_at").toInstant());
        }, userId);
    }

    private Optional<AppealView> latestAppeal(long userId) {
        return jdbc.query("""
                SELECT id, contested, reason, status, created_at, decided_at, decision_note FROM age_appeal
                WHERE user_id = ? ORDER BY created_at DESC, id DESC LIMIT 1
                """, (rs, i) -> new AppealView(rs.getLong("id"), AgeStatus.valueOf(rs.getString("contested")),
                rs.getString("reason"), rs.getString("status"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant(),
                rs.getString("decision_note")), userId).stream().findFirst();
    }

    private UserAccount requireReviewer(long userId) {
        UserAccount u = users.findById(userId).orElseThrow(CommonExceptions.Unauthenticated::new);
        if (!reviewers.contains(u.getEmail().toLowerCase(Locale.ROOT))) {
            throw new CommonExceptions.Forbidden("Só quem revisa contestações de idade acessa esta fila.");
        }
        return u;
    }

    private void save(long userId, AgeStatus status, String method, String provider, String ref, Instant pendingUntil,
                      Instant verifiedAt, String note) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO age_verification (user_id, status, method, provider, provider_ref, pending_expires_at,
                                              verified_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET status = EXCLUDED.status, method = EXCLUDED.method,
                    provider = EXCLUDED.provider, provider_ref = EXCLUDED.provider_ref,
                    pending_expires_at = EXCLUDED.pending_expires_at, verified_at = EXCLUDED.verified_at,
                    updated_at = EXCLUDED.updated_at
                """, userId, status.name(), method, provider, ref, ts(pendingUntil), ts(verifiedAt), now);
        jdbc.update("""
                INSERT INTO age_verification_event (user_id, status, method, provider, provider_ref, note, occurred_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, userId, status.name(), method, provider, ref, note, now);
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }
}
