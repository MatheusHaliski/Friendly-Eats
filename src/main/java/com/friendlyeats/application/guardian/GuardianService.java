package com.friendlyeats.application.guardian;

import com.friendlyeats.application.account.AccountExceptions;
import com.friendlyeats.application.account.AccountNotifier;
import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.age.AgeExceptions;
import com.friendlyeats.application.age.AgeGate;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.consent.ConsentService;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.ConsentPurpose;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserPreference;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserPreferenceRepository;
import com.friendlyeats.security.AppSecrets;
import com.friendlyeats.security.ClientInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Conta de menor vinculada à do responsável (RF17; ECA Digital, arts. 17, 18 e 24; LGPD, art. 14).
 *
 * <ul>
 *   <li>O cadastro de menor de 16 já cria o convite ({@link #inviteOnRegistration}); um token de uso único vai por
 *       e-mail ({@link AccountNotifier#guardianInvite}) e só o HMAC dele fica no banco.</li>
 *   <li>Aceitar exige conta ativa com a idade verificada como maior de 18 ({@link AgeGate}).</li>
 *   <li>Enquanto o vínculo não for aceito, a conta do menor fica com as proteções máximas: nada é afrouxado.</li>
 *   <li>Painel do responsável: vê as proteções, aprova ou nega os pedidos do menor, afrouxa ou religa cada
 *       {@link Protection} e consente o dado sensível do perfil (LGPD, art. 14, §1º). Tudo vai para o histórico
 *       ({@code guardian_action}).</li>
 *   <li>Desvincular: o responsável a qualquer momento; o menor só a partir dos 16. Ao desvincular, as proteções do
 *       menor de 16 voltam ao máximo e o consentimento sensível do menor de 18 é revogado.</li>
 *   <li>Vínculo pendente há mais de {@code pending-reminder-after}: aviso ao menor, sem bloquear a conta.</li>
 * </ul>
 */
@Service
public class GuardianService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]{2,}$");
    private static final SecureRandom RANDOM = new SecureRandom();
    static final Duration RESEND_INTERVAL = Duration.ofMinutes(10);

    private final JdbcTemplate jdbc;
    private final UserAccountRepository users;
    private final UserPreferenceRepository preferences;
    private final AgePolicy age;
    private final AgeGate ageGate;
    private final ConsentService consents;
    private final AccountNotifier notifier;
    private final AppSecrets secrets;
    private final Clock clock;
    private final Duration inviteTtl;
    private final Duration reminderAfter;

    public GuardianService(JdbcTemplate jdbc, UserAccountRepository users, UserPreferenceRepository preferences,
                           AgePolicy age, AgeGate ageGate, ConsentService consents, AccountNotifier notifier,
                           AppSecrets secrets, Clock clock,
                           @Value("${friendly-eats.guardian.invite-ttl:14d}") Duration inviteTtl,
                           @Value("${friendly-eats.guardian.pending-reminder-after:7d}") Duration reminderAfter) {
        this.jdbc = jdbc;
        this.users = users;
        this.preferences = preferences;
        this.age = age;
        this.ageGate = ageGate;
        this.consents = consents;
        this.notifier = notifier;
        this.secrets = secrets;
        this.clock = clock;
        this.inviteTtl = inviteTtl;
        this.reminderAfter = reminderAfter;
    }

    // ------------------------------------------------------------ views

    public enum LinkStatus { NONE, PENDING, ACTIVE }

    public record PersonView(long id, String name, String handle) {
    }

    public record ProtectionView(Protection code, String label, String description, boolean allowed) {
    }

    public record RequestView(long id, Protection protection, String label, String status, Instant createdAt,
                              Instant decidedAt) {
    }

    public record ActionView(String actor, String action, String label, String detail, Instant occurredAt) {
    }

    /** {@code GET /api/v1/me/guardian}: o vínculo do ponto de vista do menor. */
    public record MinorLinkView(boolean required, boolean eligible, LinkStatus status, String guardianEmail,
                                PersonView guardian, Instant invitedAt, Instant inviteExpiresAt, long pendingDays,
                                boolean overdue, boolean canUnlink, boolean sensitiveConsent,
                                List<ProtectionView> protections, List<RequestView> requests,
                                List<ActionView> history) {
    }

    /** Item da lista "Quem eu supervisiono". */
    public record SupervisedView(long minorId, String name, String handle, int age, Instant linkedAt,
                                 long pendingRequests) {
    }

    /** Painel de supervisão de um menor (ECA Digital, art. 18). */
    public record SupervisionView(SupervisedView minor, boolean protectionsLocked, List<ProtectionView> protections,
                                  boolean sensitiveConsent, boolean sensitiveConsentAvailable,
                                  List<RequestView> requests, List<ActionView> history) {
    }

    private record Link(long id, long minorId, Long guardianId, String guardianEmail, String status,
                        Instant invitedAt, Instant inviteExpiresAt, Instant acceptedAt, Instant sensitiveConsentAt) {
    }

    // ------------------------------------------------------------ menor

    /** Cadastro de menor de 16 (RF1): o convite nasce na mesma transação da conta. */
    public void inviteOnRegistration(UserAccount minor, String guardianEmail) {
        createLink(minor, normalize(guardianEmail));
    }

    @Transactional(readOnly = true)
    public MinorLinkView minorView(long minorId) {
        UserAccount minor = users.findById(minorId).orElseThrow();
        boolean required = age.protectedMinor(minor.getUserDateOfBirth());
        boolean eligible = !age.adult(minor.getUserDateOfBirth());
        Optional<Link> open = openLink(minorId);
        Instant now = clock.instant();
        LinkStatus status = open.map(l -> LinkStatus.valueOf(l.status())).orElse(LinkStatus.NONE);
        long pendingDays = open.filter(l -> status == LinkStatus.PENDING)
                .map(l -> Duration.between(l.invitedAt(), now).toDays()).orElse(0L);
        PersonView guardian = open.map(Link::guardianId).flatMap(users::findById)
                .map(g -> new PersonView(g.getId(), g.getName(), g.getHandle())).orElse(null);
        UserPreference pref = prefs(minor);
        return new MinorLinkView(required, eligible, status, open.map(Link::guardianEmail).orElse(null), guardian,
                open.map(Link::invitedAt).orElse(null), open.map(Link::inviteExpiresAt).orElse(null), pendingDays,
                status == LinkStatus.PENDING && pendingDays >= reminderAfter.toDays(),
                open.isPresent() && !required, open.map(Link::sensitiveConsentAt).isPresent(), protections(pref),
                open.map(l -> requests(l.id())).orElse(List.of()), history(minorId));
    }

    /**
     * Reenvia o convite pendente ({@code guardianEmail} vazio) ou convida outro e-mail. No máximo um envio a cada
     * 10 minutos; um vínculo já aceito precisa ser desfeito antes.
     */
    @Transactional
    public MinorLinkView invite(long minorId, String guardianEmail) {
        UserAccount minor = users.lockById(minorId).orElseThrow();
        if (age.adult(minor.getUserDateOfBirth())) {
            throw new AccountExceptions.Forbidden("O vínculo com o responsável é para contas de menores de 18 anos.");
        }
        Optional<Link> open = openLink(minorId);
        if (open.filter(l -> "ACTIVE".equals(l.status())).isPresent()) {
            throw new AccountExceptions.Conflict("Sua conta já está vinculada a um responsável.");
        }
        String email = guardianEmail == null || guardianEmail.isBlank() ? null : normalize(guardianEmail);
        if (email == null && open.isEmpty()) {
            throw new AccountExceptions.Invalid("Informe o e-mail do responsável.");
        }
        if (email != null && (!EMAIL.matcher(email).matches() || email.equals(minor.getEmail()))) {
            throw new AccountExceptions.Invalid("Informe um e-mail válido do responsável, diferente do seu.");
        }
        if (open.isPresent() && open.get().invitedAt().plus(RESEND_INTERVAL).isAfter(clock.instant())) {
            throw new AccountExceptions.TooManyAttempts(
                    Duration.between(clock.instant(), open.get().invitedAt().plus(RESEND_INTERVAL)));
        }
        if (open.isPresent() && (email == null || email.equals(open.get().guardianEmail()))) {
            String token = token();
            Instant now = clock.instant();
            jdbc.update("UPDATE guardian_link SET invite_token_hash = ?, invited_at = ?, invite_expires_at = ?, "
                    + "reminder_sent_at = NULL WHERE id = ?", hash(token), ts(now), ts(now.plus(inviteTtl)),
                    open.get().id());
            action(open.get().id(), "MINOR", "INVITE_RESENT", null);
            notifier.guardianInvite(open.get().guardianEmail(), minor, token, now.plus(inviteTtl));
        } else {
            open.ifPresent(l -> end(l, "MINOR", "REPLACED"));
            createLink(minor, email);
        }
        return minorView(minorId);
    }

    /** Pedido do menor de 16 para afrouxar uma proteção; o responsável decide no painel. */
    @Transactional
    public RequestView request(long minorId, String protectionCode) {
        Protection protection = protection(protectionCode);
        UserAccount minor = users.lockById(minorId).orElseThrow();
        Link link = openLink(minorId).filter(l -> "ACTIVE".equals(l.status())).orElseThrow(() ->
                new AccountExceptions.Conflict("O pedido vai para o responsável depois que ele aceitar o vínculo."));
        if (!age.protectedMinor(minor.getUserDateOfBirth())) {
            throw new AccountExceptions.Conflict("Você já pode mudar isso em Configurações → Privacidade.");
        }
        if (protection.loosened(prefs(minor))) {
            throw new AccountExceptions.Conflict("Essa proteção já está desligada.");
        }
        long id;
        try {
            id = jdbc.queryForObject("""
                    INSERT INTO guardian_request (link_id, protection, created_at) VALUES (?, ?, ?) RETURNING id
                    """, Long.class, link.id(), protection.name(), ts(clock.instant()));
        } catch (DuplicateKeyException e) {
            throw new AccountExceptions.Conflict("Esse pedido já está com o seu responsável.");
        }
        action(link.id(), "MINOR", "REQUESTED", protection.getLabel());
        users.findById(link.guardianId()).ifPresent(g -> notifier.guardianRequest(g, minor, protection.getLabel()));
        return requests(link.id()).stream().filter(r -> r.id() == id).findFirst().orElseThrow();
    }

    /** O menor desfaz o vínculo: só a partir dos 16 (antes, só o responsável). */
    @Transactional
    public MinorLinkView unlinkByMinor(long minorId, ClientInfo client) {
        UserAccount minor = users.lockById(minorId).orElseThrow();
        Link link = openLink(minorId).orElseThrow(() -> new CommonExceptions.NotFound("Não há vínculo com responsável."));
        if (age.protectedMinor(minor.getUserDateOfBirth())) {
            throw new AccountExceptions.Forbidden("Menores de 16 anos não desfazem o vínculo; o responsável pode "
                    + "desfazer pelo painel dele (ECA Digital, art. 24).");
        }
        end(link, "MINOR", "UNLINKED");
        restoreAfterUnlink(minor, client);
        return minorView(minorId);
    }

    // ------------------------------------------------------------ responsável

    /** Aceita o convite: conta ativa, maior de 18 com a idade verificada, e não pode ser a própria conta do menor. */
    @Transactional
    public SupervisedView accept(long guardianId, String token) {
        UserAccount guardian = users.lockById(guardianId).orElseThrow();
        if (token == null || token.isBlank()) {
            throw new AccountExceptions.Invalid("Abra o link do convite que chegou por e-mail.");
        }
        Link link = jdbc.query("""
                SELECT * FROM guardian_link WHERE invite_token_hash = ? AND status = 'PENDING' AND invite_expires_at > ?
                FOR UPDATE
                """, this::link, hash(token.strip()), ts(clock.instant())).stream().findFirst()
                .orElseThrow(() -> new CommonExceptions.NotFound("Convite inválido ou expirado. Peça ao menor para "
                        + "enviar outro em Configurações → Idade e responsável."));
        if (link.minorId() == guardianId) {
            throw new AccountExceptions.Forbidden("A própria conta do menor não pode ser a do responsável.");
        }
        if (guardian.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccountExceptions.Forbidden("Confirme o seu e-mail antes de aceitar o vínculo.");
        }
        if (ageGate.access(guardian) != AgeGate.Access.ALLOWED) {
            if (ageGate.access(guardian) == AgeGate.Access.VERIFY) {
                throw new AgeExceptions.VerificationRequired();
            }
            throw new AccountExceptions.Forbidden("O responsável precisa ser maior de 18 anos com a idade verificada.");
        }
        jdbc.update("""
                UPDATE guardian_link SET status = 'ACTIVE', guardian_id = ?, accepted_at = ?, invite_token_hash = NULL
                WHERE id = ?
                """, guardianId, ts(clock.instant()), link.id());
        action(link.id(), "GUARDIAN", "ACCEPTED", null);
        UserAccount minor = users.findById(link.minorId()).orElseThrow();
        notifier.guardianLinked(minor, guardian);
        return supervised(guardianId, link.minorId());
    }

    @Transactional(readOnly = true)
    public List<SupervisedView> minors(long guardianId) {
        return jdbc.queryForList("""
                SELECT minor_id FROM guardian_link WHERE guardian_id = ? AND status = 'ACTIVE' ORDER BY accepted_at
                """, Long.class, guardianId).stream().map(id -> supervised(guardianId, id)).toList();
    }

    @Transactional(readOnly = true)
    public SupervisionView panel(long guardianId, long minorId) {
        Link link = supervisedLink(guardianId, minorId);
        UserAccount minor = users.findById(minorId).orElseThrow();
        return new SupervisionView(supervised(guardianId, minorId), age.protectedMinor(minor.getUserDateOfBirth()),
                protections(prefs(minor)), link.sensitiveConsentAt() != null, !age.adult(minor.getUserDateOfBirth()),
                requests(link.id()), history(minorId));
    }

    @Transactional
    public SupervisionView decide(long guardianId, long minorId, long requestId, boolean approve) {
        Link link = supervisedLink(guardianId, minorId);
        UserAccount minor = users.lockById(minorId).orElseThrow();
        List<String> found = jdbc.queryForList("""
                SELECT protection FROM guardian_request WHERE id = ? AND link_id = ? AND status = 'PENDING' FOR UPDATE
                """, String.class, requestId, link.id());
        if (found.isEmpty()) {
            throw new CommonExceptions.NotFound("Pedido não encontrado ou já decidido.");
        }
        Protection protection = Protection.valueOf(found.getFirst());
        jdbc.update("UPDATE guardian_request SET status = ?, decided_at = ? WHERE id = ?",
                approve ? "APPROVED" : "DENIED", ts(clock.instant()), requestId);
        if (approve) {
            UserPreference pref = prefs(minor);
            protection.apply(pref, true);
            preferences.save(pref);
        }
        action(link.id(), "GUARDIAN", approve ? "APPROVED" : "DENIED", protection.getLabel());
        return panel(guardianId, minorId);
    }

    /** O responsável afrouxa ({@code allowed}) ou religa uma proteção direto no painel. */
    @Transactional
    public SupervisionView setProtection(long guardianId, long minorId, String protectionCode, boolean allowed) {
        Protection protection = protection(protectionCode);
        Link link = supervisedLink(guardianId, minorId);
        UserAccount minor = users.lockById(minorId).orElseThrow();
        UserPreference pref = prefs(minor);
        if (protection.loosened(pref) != allowed) {
            protection.apply(pref, allowed);
            preferences.save(pref);
            action(link.id(), "GUARDIAN", allowed ? "PROTECTION_ALLOWED" : "PROTECTION_RESTORED", protection.getLabel());
        }
        // um pedido pendente sobre a mesma proteção fica respondido pela mudança
        jdbc.update("""
                UPDATE guardian_request SET status = ?, decided_at = ?
                WHERE link_id = ? AND protection = ? AND status = 'PENDING'
                """, allowed ? "APPROVED" : "DENIED", ts(clock.instant()), link.id(), protection.name());
        return panel(guardianId, minorId);
    }

    /** Consentimento do responsável para o dado sensível do perfil (LGPD, art. 14, §1º). Revogar apaga os estilos. */
    @Transactional
    public SupervisionView setSensitiveConsent(long guardianId, long minorId, boolean granted, ClientInfo client) {
        Link link = supervisedLink(guardianId, minorId);
        UserAccount minor = users.findById(minorId).orElseThrow();
        if (age.adult(minor.getUserDateOfBirth())) {
            throw new AccountExceptions.Conflict("A conta já é de maior de idade: o consentimento é do titular.");
        }
        if ((link.sensitiveConsentAt() != null) != granted) {
            jdbc.update("UPDATE guardian_link SET sensitive_consent_at = ? WHERE id = ?",
                    granted ? ts(clock.instant()) : null, link.id());
            action(link.id(), "GUARDIAN", granted ? "SENSITIVE_CONSENT_GRANTED" : "SENSITIVE_CONSENT_REVOKED", null);
            if (!granted) {
                consents.set(minorId, ConsentPurpose.SENSITIVE_PROFILE_DATA.name(), false, client);
            }
        }
        return panel(guardianId, minorId);
    }

    @Transactional
    public void unlinkByGuardian(long guardianId, long minorId, ClientInfo client) {
        Link link = supervisedLink(guardianId, minorId);
        UserAccount minor = users.lockById(minorId).orElseThrow();
        end(link, "GUARDIAN", "UNLINKED");
        restoreAfterUnlink(minor, client);
    }

    // ------------------------------------------------------------ job

    /** Vínculos pendentes há mais de {@code pending-reminder-after}: um aviso ao menor (não bloqueia a conta). */
    @Transactional
    public int remindPending() {
        Instant now = clock.instant();
        List<Link> due = jdbc.query("""
                SELECT * FROM guardian_link WHERE status = 'PENDING' AND reminder_sent_at IS NULL AND invited_at < ?
                FOR UPDATE SKIP LOCKED
                """, this::link, ts(now.minus(reminderAfter)));
        for (Link l : due) {
            jdbc.update("UPDATE guardian_link SET reminder_sent_at = ? WHERE id = ?", ts(now), l.id());
            action(l.id(), "SYSTEM", "REMINDER", null);
            users.findById(l.minorId()).ifPresent(m ->
                    notifier.guardianLinkPending(m, Duration.between(l.invitedAt(), now).toDays()));
        }
        return due.size();
    }

    // ------------------------------------------------------------ eliminação da conta (RF5)

    /**
     * Conta eliminada: os vínculos em que ela é o menor saem; os que ela supervisiona terminam (o menor volta às
     * proteções máximas) e ficam sem o responsável, só com o histórico.
     */
    @Transactional
    public void eraseFor(long userId) {
        ClientInfo system = new ClientInfo(null, "Sistema", "SUPPORT");
        List<Link> supervised = jdbc.query("SELECT * FROM guardian_link WHERE guardian_id = ? AND status = 'ACTIVE'",
                this::link, userId);
        for (Link l : supervised) {
            end(l, "SYSTEM", "UNLINKED");
            users.findById(l.minorId()).ifPresent(m -> restoreAfterUnlink(m, system));
        }
        jdbc.update("UPDATE guardian_link SET guardian_id = NULL, guardian_email = 'conta-excluida' WHERE guardian_id = ?",
                userId);
        jdbc.update("DELETE FROM guardian_link WHERE minor_id = ?", userId);
    }

    // ------------------------------------------------------------ exportação (RF18)

    /** Vínculos do titular como menor e como responsável, com o histórico, para a exportação de dados. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> exportFor(long userId) {
        return jdbc.queryForList("""
                SELECT l.id, l.minor_id, l.guardian_id, l.guardian_email, l.status, l.invited_at, l.accepted_at,
                       l.ended_at, l.ended_by, l.sensitive_consent_at,
                       CASE WHEN l.minor_id = ? THEN 'MINOR' ELSE 'GUARDIAN' END AS my_role
                FROM guardian_link l WHERE l.minor_id = ? OR l.guardian_id = ? ORDER BY l.created_at
                """, userId, userId, userId);
    }

    // ------------------------------------------------------------ partes

    private void createLink(UserAccount minor, String email) {
        if (email == null || !EMAIL.matcher(email).matches()) {
            throw new AccountExceptions.Invalid("Informe um e-mail válido do responsável.");
        }
        String token = token();
        Instant now = clock.instant();
        long id = jdbc.queryForObject("""
                INSERT INTO guardian_link (minor_id, guardian_email, invite_token_hash, invited_at, invite_expires_at,
                                           created_at)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, minor.getId(), email, hash(token), ts(now), ts(now.plus(inviteTtl)), ts(now));
        action(id, "MINOR", "INVITED", null);
        notifier.guardianInvite(email, minor, token, now.plus(inviteTtl));
    }

    private void end(Link link, String by, String actionCode) {
        jdbc.update("""
                UPDATE guardian_link SET status = 'ENDED', ended_at = ?, ended_by = ?, invite_token_hash = NULL,
                                         sensitive_consent_at = NULL
                WHERE id = ?
                """, ts(clock.instant()), by, link.id());
        jdbc.update("""
                UPDATE guardian_request SET status = 'CANCELLED', decided_at = ? WHERE link_id = ? AND status = 'PENDING'
                """, ts(clock.instant()), link.id());
        action(link.id(), by, actionCode, null);
    }

    /** Sem responsável: menor de 16 volta às proteções máximas e o dado sensível do menor de 18 é revogado. */
    private void restoreAfterUnlink(UserAccount minor, ClientInfo client) {
        if (age.protectedMinor(minor.getUserDateOfBirth())) {
            UserPreference pref = prefs(minor);
            Arrays.stream(Protection.values()).forEach(p -> p.apply(pref, false));
            preferences.save(pref);
        }
        if (!age.adult(minor.getUserDateOfBirth())) {
            consents.set(minor.getId(), ConsentPurpose.SENSITIVE_PROFILE_DATA.name(), false, client);
        }
    }

    private SupervisedView supervised(long guardianId, long minorId) {
        Link link = supervisedLink(guardianId, minorId);
        UserAccount minor = users.findById(minorId).orElseThrow();
        Long pending = jdbc.queryForObject(
                "SELECT count(*) FROM guardian_request WHERE link_id = ? AND status = 'PENDING'", Long.class, link.id());
        return new SupervisedView(minorId, minor.getName(), minor.getHandle(),
                minor.getUserDateOfBirth() == null ? 0 : age.ageOf(minor.getUserDateOfBirth()), link.acceptedAt(),
                pending == null ? 0 : pending);
    }

    /** Vínculo ativo deste responsável com este menor; sem ele, 404 (não revela se o menor existe). */
    private Link supervisedLink(long guardianId, long minorId) {
        return jdbc.query("""
                SELECT * FROM guardian_link WHERE guardian_id = ? AND minor_id = ? AND status = 'ACTIVE'
                """, this::link, guardianId, minorId).stream().findFirst()
                .orElseThrow(() -> new CommonExceptions.NotFound("Você não supervisiona esta conta."));
    }

    private Optional<Link> openLink(long minorId) {
        return jdbc.query("SELECT * FROM guardian_link WHERE minor_id = ? AND status IN ('PENDING','ACTIVE')",
                this::link, minorId).stream().findFirst();
    }

    private List<ProtectionView> protections(UserPreference pref) {
        return Arrays.stream(Protection.values())
                .map(p -> new ProtectionView(p, p.getLabel(), p.getDescription(), p.loosened(pref))).toList();
    }

    private List<RequestView> requests(long linkId) {
        return jdbc.query("""
                SELECT id, protection, status, created_at, decided_at FROM guardian_request
                WHERE link_id = ? ORDER BY (status = 'PENDING') DESC, created_at DESC LIMIT 20
                """, (rs, i) -> {
            Protection p = Protection.valueOf(rs.getString("protection"));
            return new RequestView(rs.getLong("id"), p, p.getLabel(), rs.getString("status"),
                    instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("decided_at")));
        }, linkId);
    }

    /** Histórico de todos os vínculos do menor, do mais novo ao mais antigo. */
    private List<ActionView> history(long minorId) {
        return jdbc.query("""
                SELECT a.actor, a.action, a.detail, a.occurred_at FROM guardian_action a
                JOIN guardian_link l ON l.id = a.link_id
                WHERE l.minor_id = ? ORDER BY a.occurred_at DESC, a.id DESC LIMIT 50
                """, (rs, i) -> new ActionView(rs.getString("actor"), rs.getString("action"),
                label(rs.getString("action")), rs.getString("detail"), instant(rs.getTimestamp("occurred_at"))), minorId);
    }

    static String label(String action) {
        return switch (action) {
            case "INVITED" -> "Convite enviado ao responsável";
            case "INVITE_RESENT" -> "Convite reenviado";
            case "REPLACED" -> "Convite trocado por outro e-mail";
            case "ACCEPTED" -> "Vínculo aceito pelo responsável";
            case "REQUESTED" -> "Pedido para afrouxar uma proteção";
            case "APPROVED" -> "Pedido aprovado pelo responsável";
            case "DENIED" -> "Pedido negado pelo responsável";
            case "PROTECTION_ALLOWED" -> "Proteção desligada pelo responsável";
            case "PROTECTION_RESTORED" -> "Proteção religada pelo responsável";
            case "SENSITIVE_CONSENT_GRANTED" -> "Responsável consentiu os estilos alimentares no perfil";
            case "SENSITIVE_CONSENT_REVOKED" -> "Responsável revogou o consentimento dos estilos alimentares";
            case "UNLINKED" -> "Vínculo desfeito";
            case "REMINDER" -> "Aviso: vínculo pendente";
            default -> action;
        };
    }

    private void action(long linkId, String actor, String action, String detail) {
        jdbc.update("INSERT INTO guardian_action (link_id, actor, action, detail, occurred_at) VALUES (?, ?, ?, ?, ?)",
                linkId, actor, action, detail, ts(clock.instant()));
    }

    private UserPreference prefs(UserAccount u) {
        return preferences.findById(u.getId()).orElseGet(() ->
                preferences.save(UserPreference.defaultsFor(u.getId(), age.protectedMinor(u.getUserDateOfBirth()))));
    }

    private static Protection protection(String code) {
        return Protection.fromCode(code).orElseThrow(() -> new CommonExceptions.NotFound("Proteção desconhecida."));
    }

    private Link link(ResultSet rs, int i) throws SQLException {
        long g = rs.getLong("guardian_id");
        Long guardian = rs.wasNull() ? null : g;
        return new Link(rs.getLong("id"), rs.getLong("minor_id"), guardian, rs.getString("guardian_email"),
                rs.getString("status"), instant(rs.getTimestamp("invited_at")),
                instant(rs.getTimestamp("invite_expires_at")), instant(rs.getTimestamp("accepted_at")),
                instant(rs.getTimestamp("sensitive_consent_at")));
    }

    private String token() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private String hash(String token) {
        return secrets.fingerprint("guardian-invite", token);
    }

    private static String normalize(String email) {
        return email == null ? null : UserAccount.normalizeEmail(email);
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
