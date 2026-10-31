package com.friendlyeats.application.moderation;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.moderation.ModerationTargets.Target;
import com.friendlyeats.application.moderation.ModerationViews.AppealDecisionRequest;
import com.friendlyeats.application.moderation.ModerationViews.AppealRequest;
import com.friendlyeats.application.moderation.ModerationViews.AppealView;
import com.friendlyeats.application.moderation.ModerationViews.AuthorityReferralRequest;
import com.friendlyeats.application.moderation.ModerationViews.CaseDetailView;
import com.friendlyeats.application.moderation.ModerationViews.CasePageView;
import com.friendlyeats.application.moderation.ModerationViews.CaseSummaryView;
import com.friendlyeats.application.moderation.ModerationViews.CountView;
import com.friendlyeats.application.moderation.ModerationViews.DecisionRequest;
import com.friendlyeats.application.moderation.ModerationViews.DecisionView;
import com.friendlyeats.application.moderation.ModerationViews.MyDecisionView;
import com.friendlyeats.application.moderation.ModerationViews.PriorityView;
import com.friendlyeats.application.moderation.ModerationViews.ReportView;
import com.friendlyeats.application.moderation.ModerationViews.SignalView;
import com.friendlyeats.application.moderation.ModerationViews.StatsView;
import com.friendlyeats.domain.moderation.CaseStatus;
import com.friendlyeats.domain.moderation.ModerationAction;
import com.friendlyeats.domain.moderation.ModerationPriority;
import com.friendlyeats.domain.moderation.ReportReason;
import com.friendlyeats.domain.moderation.ReportSource;
import com.friendlyeats.domain.moderation.TargetType;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.AppealRow;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.CaseRow;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.Count;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.DecisionRow;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.ReportRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Fila, decisão motivada e recurso (RF13 · RC-SEG-02). Regras:
 * <ul>
 *   <li>toda decisão tem ação, regra da política (menos "manter") e fundamentação; fica no histórico só de inclusão;</li>
 *   <li>quem decide não pode ser o autor do conteúdo nem quem o denunciou;</li>
 *   <li>o autor é avisado da medida com o motivo, a origem (humana ou automática), como recorrer e os prazos
 *       (ECA Digital, art. 30); quem denunciou vê o resultado e pode recorrer de um arquivamento;</li>
 *   <li>o recurso é revisto por outra pessoa moderadora, nunca por quem decidiu ({@code 403}).</li>
 * </ul>
 */
@Service
public class ModerationService {

    static final int MIN_STATEMENT = 20;
    static final int MAX_STATEMENT = 2000;
    static final int MAX_PAGE = 50;

    private final ModerationRepository repo;
    private final ModerationTargets targets;
    private final ModerationRoles roles;
    private final ModerationPolicy policy;
    private final ModerationNotifier notifier;
    private final ReviewIntegrityService integrity;
    private final Clock clock;

    public ModerationService(ModerationRepository repo, ModerationTargets targets, ModerationRoles roles,
                             ModerationPolicy policy, ModerationNotifier notifier, ReviewIntegrityService integrity,
                             Clock clock) {
        this.repo = repo;
        this.targets = targets;
        this.roles = roles;
        this.policy = policy;
        this.notifier = notifier;
        this.integrity = integrity;
        this.clock = clock;
    }

    // ------------------------------------------------------------ fila

    @Transactional(readOnly = true)
    public CasePageView queue(long moderatorId, String statusCode, Integer page, Integer size) {
        roles.requireModerator(moderatorId);
        CaseStatus status = statusCode == null || statusCode.isBlank() ? CaseStatus.OPEN : parse(statusCode);
        int s = size == null ? 20 : Math.clamp(size, 1, MAX_PAGE);
        int p = page == null ? 0 : Math.max(0, page);
        Instant now = clock.instant();
        List<CaseSummaryView> items = repo.queue(status, p * s, s).stream().map(c -> summary(c, now)).toList();
        return new CasePageView(items, p, s, repo.countByStatus(status));
    }

    @Transactional(readOnly = true)
    public CaseDetailView detail(long moderatorId, long caseId) {
        roles.requireModerator(moderatorId);
        return detail(requireCase(caseId), moderatorId);
    }

    // ------------------------------------------------------------ decidir

    @Transactional
    public CaseDetailView decide(long moderatorId, long caseId, DecisionRequest req) {
        roles.requireModerator(moderatorId);
        CaseRow c = requireCase(caseId);
        if (c.status() != CaseStatus.OPEN) {
            throw new CommonExceptions.Conflict("Este caso já foi decidido. Recursos são revistos na aba Em recurso.");
        }
        conflictOfInterest(c, moderatorId).ifPresent(reason -> {
            throw new CommonExceptions.Forbidden(reason);
        });
        List<String> errors = new ArrayList<>();
        ModerationAction action = action(req == null ? null : req.action(), c.targetType(), errors);
        ReportReason policyReason = policy(req == null ? null : req.policyReason(), action, errors);
        String statement = statement(req == null ? null : req.statement(), errors);
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        targets.apply(c.targetType(), c.targetKey(), action);
        repo.insertDecision(c.id(), "FIRST", action, policyReason, statement, false, moderatorId);
        repo.setCaseStatus(c.id(), CaseStatus.DECIDED, true);
        boolean upheld = action != ModerationAction.KEEP;
        repo.resolveReports(c.id(), upheld ? "UPHELD" : "DISMISSED");
        String protocol = caseProtocol(c.id());
        if (upheld && c.targetOwnerId() != null) {
            notifier.decisionToAuthor(c.targetOwnerId(), protocol, action.name(), policyReason.name(), false);
        }
        for (ReportRow r : repo.reportsOf(c.id())) {
            if (r.source() != ReportSource.SYSTEM) {
                notifier.outcomeToReporter(r.reporterId(), r.reporterEmail(),
                        ReportService.protocol(r.id(), r.createdAt()), upheld);
            }
        }
        return detail(requireCase(caseId), moderatorId);
    }

    /** Comunicação às autoridades registrada pela moderação (indício de abuso sexual infantil). */
    @Transactional
    public CaseDetailView recordAuthorityReferral(long moderatorId, long caseId, AuthorityReferralRequest req) {
        roles.requireModerator(moderatorId);
        CaseRow c = requireCase(caseId);
        String reference = req == null || req.reference() == null ? "" : req.reference().strip();
        if (reference.length() < 3 || reference.length() > 200) {
            throw new CommonExceptions.Invalid(List.of("Informe o protocolo ou a referência da comunicação (3 a 200 "
                    + "caracteres)."));
        }
        if (c.topReason() != ReportReason.CRIANCA_EM_RISCO && "NONE".equals(c.authorityReferral())) {
            throw new CommonExceptions.Conflict("Só casos de criança em risco são comunicados às autoridades por aqui.");
        }
        repo.recordAuthorityReferral(c.id(), reference);
        return detail(requireCase(caseId), moderatorId);
    }

    // ------------------------------------------------------------ recurso

    /** Recurso do autor (contra medida) ou de quem denunciou (contra arquivamento), no prazo, uma vez por decisão. */
    @Transactional
    public AppealView appeal(long viewerId, long caseId, AppealRequest req) {
        CaseRow c = repo.findCase(caseId).orElseThrow(ModerationService::caseNotFound);
        Optional<DecisionRow> first = repo.decisionsOf(c.id()).stream().filter(d -> d.stage().equals("FIRST")).findFirst();
        boolean author = Objects.equals(c.targetOwnerId(), viewerId)
                && first.map(d -> d.action() != ModerationAction.KEEP).orElse(false);
        boolean reporter = repo.reportsOf(c.id()).stream()
                .anyMatch(r -> Objects.equals(r.reporterId(), viewerId) && "DISMISSED".equals(r.status()))
                && first.map(d -> d.action() == ModerationAction.KEEP).orElse(false);
        if (!author && !reporter) {
            // nem autor afetado nem denunciante arquivado: não revela o caso
            if (!Objects.equals(c.targetOwnerId(), viewerId)
                    && repo.reportsOf(c.id()).stream().noneMatch(r -> Objects.equals(r.reporterId(), viewerId))) {
                throw caseNotFound();
            }
            throw new CommonExceptions.Conflict("Este caso ainda não tem decisão que aceite recurso.");
        }
        if (c.status() != CaseStatus.DECIDED) {
            throw new CommonExceptions.Conflict(c.status() == CaseStatus.IN_APPEAL
                    ? "Já há um recurso em análise neste caso." : "Este caso já foi revisto em recurso.");
        }
        DecisionRow decision = first.orElseThrow();
        Instant deadline = decision.createdAt().plus(policy.appealWindow());
        if (!clock.instant().isBefore(deadline)) {
            throw new CommonExceptions.Conflict("O prazo de " + policy.appealWindow().toDays()
                    + " dias para recorrer desta decisão terminou.");
        }
        String reason = req == null || req.reason() == null ? "" : req.reason().strip();
        if (reason.length() < MIN_STATEMENT || reason.length() > MAX_STATEMENT) {
            throw new CommonExceptions.Invalid(List.of("Explique o recurso em " + MIN_STATEMENT + " a " + MAX_STATEMENT
                    + " caracteres."));
        }
        long id = repo.insertAppeal(c.id(), decision.id(), viewerId, author ? "AUTHOR" : "REPORTER", reason);
        repo.setCaseStatus(c.id(), CaseStatus.IN_APPEAL, false);
        return repo.appealsOf(c.id()).stream().filter(a -> a.id() == id).findFirst().map(ModerationService::appealView)
                .orElseThrow();
    }

    /** Decisão do recurso por outra pessoa moderadora: mantém ou reverte, com fundamentação. */
    @Transactional
    public CaseDetailView decideAppeal(long moderatorId, long caseId, AppealDecisionRequest req) {
        roles.requireModerator(moderatorId);
        CaseRow c = requireCase(caseId);
        if (c.status() != CaseStatus.IN_APPEAL) {
            throw new CommonExceptions.Conflict("Este caso não tem recurso aberto.");
        }
        List<AppealRow> open = repo.appealsOf(c.id()).stream().filter(a -> a.status().equals("OPEN")).toList();
        List<DecisionRow> decisions = repo.decisionsOf(c.id());
        DecisionRow contested = decisions.stream().filter(d -> d.id() == open.getFirst().decisionId()).findFirst()
                .orElseThrow();
        if (Objects.equals(contested.moderatorId(), moderatorId)) {
            throw new CommonExceptions.Forbidden("O recurso precisa ser revisto por outra pessoa moderadora, não por "
                    + "quem tomou a decisão.");
        }
        conflictOfInterest(c, moderatorId).ifPresent(reason -> {
            throw new CommonExceptions.Forbidden(reason);
        });
        List<String> errors = new ArrayList<>();
        String outcome = req == null || req.outcome() == null ? "" : req.outcome().strip().toUpperCase(Locale.ROOT);
        if (!outcome.equals("MAINTAINED") && !outcome.equals("REVERSED")) {
            errors.add("Diga o resultado do recurso: MAINTAINED (mantém a decisão) ou REVERSED (reverte).");
        }
        String statement = statement(req == null ? null : req.statement(), errors);
        ModerationAction action = contested.action();
        ReportReason policyReason = contested.policyReason();
        if (outcome.equals("REVERSED")) {
            if (req.action() == null || req.action().isBlank()) {
                if (contested.action() == ModerationAction.KEEP) {
                    errors.add("Ao reverter um arquivamento, escolha a medida (ocultar, remover ou avisar o autor).");
                    action = null;
                } else {
                    action = ModerationAction.KEEP;
                }
            } else {
                action = action(req.action(), c.targetType(), errors);
            }
            if (action != null && action == contested.action()) {
                errors.add("Reverter exige uma medida diferente da decisão contestada.");
            }
            policyReason = policy(req.policyReason(), action, errors);
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        boolean reversed = outcome.equals("REVERSED");
        if (reversed) {
            targets.apply(c.targetType(), c.targetKey(), action);
            repo.setReportsOutcome(c.id(), action != ModerationAction.KEEP ? "UPHELD" : "DISMISSED");
        }
        long decisionId = repo.insertDecision(c.id(), "APPEAL", action, policyReason, statement, false, moderatorId);
        repo.resolveAppeals(c.id(), reversed ? "REVERSED" : "MAINTAINED", decisionId);
        repo.setCaseStatus(c.id(), CaseStatus.FINAL, false);
        for (AppealRow a : open) {
            if (a.appellantId() != null) {
                notifier.appealResult(a.appellantId(), caseProtocol(c.id()), reversed);
            }
        }
        return detail(requireCase(caseId), moderatorId);
    }

    // ------------------------------------------------------------ autor do conteúdo

    /** "Decisões sobre meu conteúdo": só casos com medida; nunca mostra quem denunciou nem quem decidiu. */
    @Transactional(readOnly = true)
    public List<MyDecisionView> myDecisions(long viewerId) {
        Instant now = clock.instant();
        List<MyDecisionView> out = new ArrayList<>();
        for (CaseRow c : repo.casesWithMeasureOwnedBy(viewerId)) {
            List<DecisionRow> decisions = repo.decisionsOf(c.id());
            Optional<DecisionRow> first = decisions.stream().filter(d -> d.stage().equals("FIRST")).findFirst();
            DecisionRow shown = first.orElse(decisions.getLast());
            Optional<AppealRow> appeal = repo.appealsOf(c.id()).stream()
                    .filter(a -> Objects.equals(a.appellantId(), viewerId)).findFirst();
            DecisionView result = appeal.flatMap(a -> decisions.stream()
                    .filter(d -> a.resultDecisionId() != null && d.id() == a.resultDecisionId()).findFirst())
                    .map(d -> decisionView(d, false)).orElse(null);
            Instant deadline = first.map(d -> d.createdAt().plus(policy.appealWindow())).orElse(null);
            boolean canAppeal = first.isPresent() && first.get().action() != ModerationAction.KEEP
                    && c.status() == CaseStatus.DECIDED && appeal.isEmpty() && now.isBefore(deadline);
            String how = first.isEmpty()
                    ? "A retirada é preventiva: uma pessoa da moderação vai decidir. Depois da decisão você pode recorrer."
                    : canAppeal ? "Recorra em até " + policy.appealWindow().toDays() + " dias: outra pessoa da "
                    + "moderação revê a decisão e responde em até " + policy.appealResponseDays() + " dias."
                    : appeal.isPresent() ? "Seu recurso foi registrado." : "O prazo de recurso terminou.";
            out.add(new MyDecisionView(c.id(), caseProtocol(c.id()),
                    targets.view(c.targetType(), c.targetKey(), c.snapshot(), Optional.empty()),
                    decisionView(shown, false), canAppeal, first.isPresent() ? deadline : null,
                    policy.appealResponseDays(), how, appeal.map(ModerationService::appealView).orElse(null), result));
        }
        return out;
    }

    // ------------------------------------------------------------ relatório

    @Transactional(readOnly = true)
    public StatsView stats(long moderatorId, Integer days) {
        roles.requireModerator(moderatorId);
        int d = days == null ? 30 : Math.clamp(days, 1, 365);
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(d));
        return new StatsView(from, to, repo.countByStatus(CaseStatus.OPEN), repo.countByStatus(CaseStatus.IN_APPEAL),
                repo.overdue(to),
                counts(repo.reportsByReason(from, to), code -> ReportReason.valueOf(code).getLabel()),
                counts(repo.reportsBySource(from, to), code -> ReportSource.valueOf(code).getLabel()),
                counts(repo.decisionsByAction(from, to), ModerationService::decisionLabel),
                counts(repo.decisionsByPolicy(from, to), code -> ReportReason.valueOf(code).getLabel()),
                counts(repo.appealsByStatus(from, to), code -> switch (code) {
                    case "OPEN" -> "Em análise";
                    case "MAINTAINED" -> "Mantidas";
                    default -> "Revertidas";
                }),
                counts(repo.authorityReferrals(from, to), code -> code.equals("SENT") ? "Comunicadas" : "A comunicar"));
    }

    // ------------------------------------------------------------ montagem

    private CaseDetailView detail(CaseRow c, long moderatorId) {
        Instant now = clock.instant();
        Optional<Target> current = targets.current(c.targetType(), c.targetKey());
        List<ReportView> reports = repo.reportsOf(c.id()).stream().map(ModerationService::reportView).toList();
        List<SignalView> signals = c.targetType() == TargetType.REVIEW
                ? integrity.signals(Long.parseLong(c.targetKey())) : List.of();
        List<DecisionRow> decisionRows = repo.decisionsOf(c.id());
        List<DecisionView> decisions = decisionRows.stream().map(d -> decisionView(d, true)).toList();
        List<AppealView> appeals = repo.appealsOf(c.id()).stream().map(ModerationService::appealView).toList();
        String blocked = conflictOfInterest(c, moderatorId).orElse(null);
        if (blocked == null && c.status() == CaseStatus.IN_APPEAL) {
            long contested = repo.appealsOf(c.id()).stream().filter(a -> a.status().equals("OPEN"))
                    .map(AppealRow::decisionId).findFirst().orElse(-1L);
            boolean same = decisionRows.stream().anyMatch(d -> d.id() == contested
                    && Objects.equals(d.moderatorId(), moderatorId));
            if (same) {
                blocked = "Você tomou a decisão contestada: o recurso precisa ser revisto por outra pessoa moderadora.";
            }
        } else if (blocked == null && c.status() != CaseStatus.OPEN) {
            blocked = c.status() == CaseStatus.DECIDED ? "Caso decidido; aguardando o prazo de recurso."
                    : "Caso encerrado.";
        }
        List<Coded> allowed = c.targetType().actions().stream()
                .map(a -> new Coded(a.name(), a.getLabel(), null)).toList();
        return new CaseDetailView(summary(c, now), c.snapshot(),
                current.map(Target::state).orElse("Não existe mais"), c.targetOwnerId(), c.establishmentId(),
                c.authorityReference(), c.authoritySentAt(), reports, signals, decisions, appeals, allowed,
                blocked == null, blocked);
    }

    private CaseSummaryView summary(CaseRow c, Instant now) {
        ModerationPriority p = ModerationPriority.ofLevel(c.priority());
        boolean active = c.status() == CaseStatus.OPEN || c.status() == CaseStatus.IN_APPEAL;
        return new CaseSummaryView(c.id(), caseProtocol(c.id()),
                targets.view(c.targetType(), c.targetKey(), c.snapshot(), targets.current(c.targetType(), c.targetKey())),
                new Coded(c.topReason().name(), c.topReason().getLabel(), null),
                new PriorityView(p.name(), p.getLabel(), p.level()),
                new Coded(c.status().name(), c.status().getLabel(), null), c.reportCount(), c.preventive(),
                c.authorityReferral(), c.dueAt(), active && c.dueAt().isBefore(now), c.createdAt(), c.updatedAt());
    }

    private Optional<String> conflictOfInterest(CaseRow c, long moderatorId) {
        if (Objects.equals(c.targetOwnerId(), moderatorId)) {
            return Optional.of("O conteúdo é seu: outra pessoa da moderação precisa decidir.");
        }
        if (repo.reportsOf(c.id()).stream().anyMatch(r -> Objects.equals(r.reporterId(), moderatorId))) {
            return Optional.of("Você denunciou este conteúdo: outra pessoa da moderação precisa decidir.");
        }
        return Optional.empty();
    }

    private static ReportView reportView(ReportRow r) {
        String reporter = switch (r.source()) {
            case USER -> "Conta #" + r.reporterId();
            case PUBLIC_FORM -> r.reporterName() + " <" + r.reporterEmail() + ">";
            case OWNER_CONTEST -> "Dono do lugar (conta #" + r.reporterId() + ")";
            case SYSTEM -> "Filtro antifraude";
        };
        return new ReportView(r.id(), ReportService.protocol(r.id(), r.createdAt()),
                new Coded(r.source().name(), r.source().getLabel(), null),
                new Coded(r.reason().name(), r.reason().getLabel(), null), r.details(), reporter, r.lowTrust(),
                r.childSexualAbuse(), r.status(), r.createdAt(), r.resolvedAt());
    }

    /** {@code withModerator}: só a moderação vê quem decidiu; o autor vê a origem (humana ou automática). */
    static DecisionView decisionView(DecisionRow d, boolean withModerator) {
        String origin = d.automated()
                ? "Decisão automática, revisada por uma pessoa da moderação"
                : "Decisão de uma pessoa da moderação";
        return new DecisionView(d.id(), d.stage(), new Coded(d.action().name(), d.action().getLabel(), null),
                d.policyReason() == null ? null : new Coded(d.policyReason().name(), d.policyReason().getLabel(), null),
                d.statement(), d.automated(), origin, withModerator ? d.moderatorId() : null, d.createdAt());
    }

    private static AppealView appealView(AppealRow a) {
        return new AppealView(a.id(), a.role(), a.reason(), a.status(), a.decisionId(), a.resultDecisionId(),
                a.createdAt(), a.resolvedAt());
    }

    private static List<CountView> counts(List<Count> rows, Function<String, String> label) {
        return rows.stream().map(r -> new CountView(r.code(), label.apply(r.code()), r.count())).toList();
    }

    private static String decisionLabel(String code) {
        String[] p = code.split(":");
        String stage = switch (p[0]) {
            case "PREVENTIVE" -> "Preventiva";
            case "APPEAL" -> "Recurso";
            default -> "Decisão";
        };
        return stage + " · " + ModerationAction.valueOf(p[1]).getLabel() + " · " + (p[2].equals("A") ? "automática"
                : "humana");
    }

    private CaseRow requireCase(long caseId) {
        return repo.findCase(caseId).orElseThrow(ModerationService::caseNotFound);
    }

    private static CommonExceptions.NotFound caseNotFound() {
        return new CommonExceptions.NotFound("Caso não encontrado.");
    }

    private static CaseStatus parse(String code) {
        try {
            return CaseStatus.valueOf(code.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Situação desconhecida: use OPEN, DECIDED, IN_APPEAL ou FINAL.");
        }
    }

    private static ModerationAction action(String code, TargetType type, List<String> errors) {
        ModerationAction a = null;
        try {
            a = code == null ? null : ModerationAction.valueOf(code.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // cai na mensagem abaixo
        }
        if (a == null) {
            errors.add("Escolha a decisão: KEEP (manter), HIDE (ocultar), REMOVE (remover) ou WARN (avisar o autor).");
        } else if (!type.actions().contains(a)) {
            errors.add("\"" + a.getLabel() + "\" não vale para " + type.getLabel().toLowerCase(Locale.ROOT) + ".");
        }
        return a;
    }

    private static ReportReason policy(String code, ModerationAction action, List<String> errors) {
        if (action == null || action == ModerationAction.KEEP) {
            return code == null || code.isBlank() ? null : ReportReason.fromCode(code).orElse(null);
        }
        ReportReason r = ReportReason.fromCode(code).orElse(null);
        if (r == null) {
            errors.add("Diga qual regra da política o conteúdo violou.");
        }
        return r;
    }

    private static String statement(String text, List<String> errors) {
        String s = text == null ? "" : text.strip();
        if (s.length() < MIN_STATEMENT || s.length() > MAX_STATEMENT) {
            errors.add("Escreva a fundamentação da decisão em " + MIN_STATEMENT + " a " + MAX_STATEMENT
                    + " caracteres: ela vai para o autor.");
        }
        return s;
    }

    /** "CASO-000042". */
    public static String caseProtocol(long caseId) {
        return "CASO-%06d".formatted(caseId);
    }
}
