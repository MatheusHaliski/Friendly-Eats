package com.friendlyeats.application.moderation;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.moderation.ModerationTargets.Target;
import com.friendlyeats.application.moderation.ModerationViews.ContestRequest;
import com.friendlyeats.application.moderation.ModerationViews.MyReportView;
import com.friendlyeats.application.moderation.ModerationViews.PublicReportRequest;
import com.friendlyeats.application.moderation.ModerationViews.ReasonView;
import com.friendlyeats.application.moderation.ModerationViews.ReceiptView;
import com.friendlyeats.application.moderation.ModerationViews.ReportRequest;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.moderation.CaseStatus;
import com.friendlyeats.domain.moderation.ModerationAction;
import com.friendlyeats.domain.moderation.ModerationPriority;
import com.friendlyeats.domain.moderation.ReportReason;
import com.friendlyeats.domain.moderation.ReportSource;
import com.friendlyeats.domain.moderation.TargetType;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.AppealRow;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.CaseRow;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.DecisionRow;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository.MyReportRow;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Denúncia (RF13 · RC-SEG-01 e 03): pelo botão "Denunciar" (com conta), pelo formulário público (sem conta, STF item 9)
 * e pela contestação de avaliação do dono do lugar (RC-SEG-08). Cada denúncia ganha protocolo e entra no caso aberto
 * do alvo (um por alvo), com prioridade pelo motivo.
 * <ul>
 *   <li>só se denuncia o que se consegue ver, e nunca o próprio conteúdo;</li>
 *   <li>uma denúncia aberta por pessoa e alvo ({@code 409});</li>
 *   <li>limite de ritmo por conta e, no formulário público, por origem (HMAC do IP) ({@code 429});</li>
 *   <li>quem tem muitas denúncias improcedentes desce um nível na fila, sem sanção (ECA Digital, arts. 32 e 33);</li>
 *   <li>criança em risco: prioridade crítica e retirada preventiva na hora de post, comentário e avaliação (Decreto
 *       12.880, arts. 41 a 44); com indício de abuso sexual, o caso fica marcado para comunicação às autoridades,
 *       que a moderação registra (nada é enviado automaticamente).</li>
 * </ul>
 */
@Service
public class ReportService {

    static final int MAX_DETAILS = 1000;
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]{1,64}@[^\\s@]{1,190}\\.[^\\s@]{2,}$");
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR")).withZone(ZoneId.of("America/Sao_Paulo"));

    private final ModerationRepository repo;
    private final ModerationTargets targets;
    private final ModerationPolicy policy;
    private final ReviewRepository reviews;
    private final EstablishmentRepository establishments;
    private final Clock clock;

    public ReportService(ModerationRepository repo, ModerationTargets targets, ModerationPolicy policy,
                         ReviewRepository reviews, EstablishmentRepository establishments, Clock clock) {
        this.repo = repo;
        this.targets = targets;
        this.policy = policy;
        this.reviews = reviews;
        this.establishments = establishments;
        this.clock = clock;
    }

    /** Motivos com rótulo, descrição, prioridade e onde valem (público: o diálogo e o formulário usam). */
    public List<ReasonView> reasons() {
        return Arrays.stream(ReportReason.values()).map(r -> new ReasonView(r.name(), r.getLabel(), r.getDescription(),
                r.priority().name(), r.priority().getLabel(), r.targets().stream().map(Enum::name).sorted().toList(),
                r.ownerContest())).toList();
    }

    // ------------------------------------------------------------ com conta

    @Transactional
    public ReceiptView report(long viewerId, ReportRequest req) {
        if (req == null) {
            throw new CommonExceptions.Invalid(List.of("Envie o tipo e o id do conteúdo, e o motivo."));
        }
        List<String> errors = new ArrayList<>();
        TargetType type = type(req.targetType(), errors);
        ReportReason reason = reason(req.reason(), type, errors);
        String details = details(req.details(), reason, errors);
        if (req.targetId() == null || req.targetId().isBlank()) {
            errors.add("Diga qual conteúdo você está denunciando.");
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        Instant now = clock.instant();
        if (repo.countReportsBy(viewerId, now.minus(Duration.ofHours(1))) >= policy.reportsPerHour()
                || repo.countReportsBy(viewerId, now.minus(Duration.ofDays(1))) >= policy.reportsPerDay()) {
            throw new CommonExceptions.TooManyRequests("Você enviou muitas denúncias em pouco tempo. Tente de novo mais "
                    + "tarde; as que já mandou estão na fila.", Duration.ofHours(1));
        }
        Target target = targets.forReport(type, req.targetId().strip(), Optional.of(viewerId));
        if (Objects.equals(target.ownerId(), viewerId)) {
            throw new CommonExceptions.Invalid(List.of("Você não pode denunciar o próprio conteúdo. Para tirá-lo do ar, "
                    + "apague ou edite."));
        }
        boolean csa = reason == ReportReason.CRIANCA_EM_RISCO && Boolean.TRUE.equals(req.childSexualAbuse());
        return submit(target, ReportSource.USER, viewerId, null, null, reason, details, csa, lowTrust(viewerId), null);
    }

    // ------------------------------------------------------------ sem conta (formulário público)

    @Transactional
    public ReceiptView publicReport(PublicReportRequest req, String ipHash) {
        if (req == null) {
            throw new CommonExceptions.Invalid(List.of("Preencha o formulário de denúncia."));
        }
        List<String> errors = new ArrayList<>();
        String name = req.name() == null ? "" : req.name().strip();
        String email = req.email() == null ? "" : req.email().strip().toLowerCase(Locale.ROOT);
        if (name.length() < 2 || name.length() > 120) {
            errors.add("Informe seu nome (2 a 120 caracteres): a denúncia não pode ser anônima.");
        }
        if (!EMAIL.matcher(email).matches() || email.length() > 254) {
            errors.add("Informe um e-mail válido para receber o protocolo e o resultado.");
        }
        TargetType type = type(req.targetType(), errors);
        ReportReason reason = reason(req.reason(), type, errors);
        String details = details(req.details(), reason, errors);
        if (req.targetId() == null || req.targetId().isBlank()) {
            errors.add("Cole o link do conteúdo que você quer denunciar.");
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        if (repo.countPublicReportsFrom(ipHash, clock.instant().minus(Duration.ofHours(1))) >= policy.publicReportsPerHour()) {
            throw new CommonExceptions.TooManyRequests("Muitas denúncias saíram desta conexão em pouco tempo. Tente de "
                    + "novo mais tarde.", Duration.ofHours(1));
        }
        Target target = targets.forReport(type, req.targetId().strip(), Optional.empty());
        boolean csa = reason == ReportReason.CRIANCA_EM_RISCO && Boolean.TRUE.equals(req.childSexualAbuse());
        return submit(target, ReportSource.PUBLIC_FORM, null, name, email, reason, details, csa, false, ipHash);
    }

    // ------------------------------------------------------------ contestação do dono (RC-SEG-08)

    @Transactional
    public ReceiptView contest(long viewerId, long establishmentId, long reviewId, ContestRequest req) {
        Review review = reviews.findById(reviewId)
                .filter(r -> r.getEstablishmentId() == establishmentId && r.getStatus() == Review.Status.PUBLISHED)
                .orElseThrow(() -> new CommonExceptions.NotFound("Avaliação não encontrada."));
        Establishment place = establishments.findById(establishmentId)
                .orElseThrow(() -> new CommonExceptions.NotFound("Estabelecimento não encontrado."));
        if (!Objects.equals(place.getOwnerId(), viewerId)) {
            throw new CommonExceptions.Forbidden("Só o dono do lugar contesta as avaliações dele. Para outros casos, use "
                    + "\"Denunciar\".");
        }
        List<String> errors = new ArrayList<>();
        ReportReason reason = req == null ? null : ReportReason.fromCode(req.reason()).orElse(null);
        if (reason == null || !reason.ownerContest()) {
            errors.add("Escolha um motivo da política: não é experiência própria, conflito de interesse, ofensa ou "
                    + "acusação de crime, dado pessoal exposto ou fora do tema. Discordar da nota não é motivo de "
                    + "contestação.");
        }
        String details = req == null || req.details() == null ? "" : req.details().strip();
        if (details.length() < 20 || details.length() > MAX_DETAILS) {
            errors.add("Explique a contestação em 20 a " + MAX_DETAILS + " caracteres.");
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        Instant now = clock.instant();
        if (repo.countOwnerContests(establishmentId, now.minus(Duration.ofDays(30))) >= policy.ownerContestsPerMonth()) {
            throw new CommonExceptions.TooManyRequests("Este lugar chegou ao limite de " + policy.ownerContestsPerMonth()
                    + " contestações em 30 dias.", Duration.ofDays(1));
        }
        Target target = targets.current(TargetType.REVIEW, Long.toString(review.getId())).orElseThrow();
        return submit(target, ReportSource.OWNER_CONTEST, viewerId, null, null, reason, details, false,
                lowTrust(viewerId), null);
    }

    // ------------------------------------------------------------ minhas denúncias

    @Transactional(readOnly = true)
    public List<MyReportView> mine(long viewerId) {
        Instant now = clock.instant();
        List<MyReportView> out = new ArrayList<>();
        for (MyReportRow row : repo.reportsBy(viewerId)) {
            CaseRow c = row.caseRow();
            List<DecisionRow> decisions = repo.decisionsOf(c.id());
            List<AppealRow> appeals = repo.appealsOf(c.id());
            Optional<DecisionRow> first = decisions.stream().filter(d -> d.stage().equals("FIRST")).findFirst();
            Optional<AppealRow> myAppeal = appeals.stream().filter(a -> Objects.equals(a.appellantId(), viewerId)).findFirst();
            Instant deadline = first.map(d -> d.createdAt().plus(policy.appealWindow())).orElse(null);
            boolean canAppeal = "DISMISSED".equals(row.report().status()) && c.status() == CaseStatus.DECIDED
                    && first.map(d -> d.action() == ModerationAction.KEEP).orElse(false) && myAppeal.isEmpty()
                    && deadline != null && now.isBefore(deadline);
            String status = switch (row.report().status()) {
                case "OPEN" -> "RECEBIDA";
                default -> c.status() == CaseStatus.IN_APPEAL ? "EM_RECURSO" : "DECIDIDA";
            };
            String statusLabel = switch (status) {
                case "RECEBIDA" -> "Recebida, em análise";
                case "EM_RECURSO" -> "Em recurso";
                default -> "Decidida";
            };
            String outcome = switch (row.report().status()) {
                case "UPHELD" -> "PROCEDENTE";
                case "DISMISSED" -> "IMPROCEDENTE";
                default -> null;
            };
            String outcomeLabel = outcome == null ? null : outcome.equals("PROCEDENTE")
                    ? "Procedente: o conteúdo recebeu uma medida" : "Improcedente: o conteúdo ficou no ar";
            ReportReason r = row.report().reason();
            out.add(new MyReportView(protocol(row.report().id(), row.report().createdAt()),
                    targets.view(c.targetType(), c.targetKey(), c.snapshot(), Optional.empty()),
                    new Coded(r.name(), r.getLabel(), null), status, statusLabel, outcome, outcomeLabel,
                    row.report().createdAt(), row.report().resolvedAt(), c.id(), canAppeal,
                    "IMPROCEDENTE".equals(outcome) ? deadline : null, myAppeal.map(AppealRow::status).orElse(null)));
        }
        return out;
    }

    // ------------------------------------------------------------ regras comuns

    private ReceiptView submit(Target t, ReportSource source, Long reporterId, String name, String email,
                               ReportReason reason, String details, boolean csa, boolean lowTrust, String ipHash) {
        ModerationPriority priority = lowTrust ? reason.priority().lowered() : reason.priority();
        Instant now = clock.instant();
        Instant due = now.plus(priority.sla());
        boolean preventive = reason == ReportReason.CRIANCA_EM_RISCO && t.type().preventiveHide() && !lowTrust;
        long caseId = repo.openCase(t.type(), t.key(), t.ownerId(), t.establishmentId(), priority.level(), reason,
                t.snapshot(), due);
        if (reporterId != null && repo.hasOpenReport(caseId, reporterId)) {
            throw new CommonExceptions.Conflict("Você já denunciou este conteúdo e a denúncia está em análise. "
                    + "Acompanhe em Minhas denúncias.");
        }
        boolean wasPreventive = repo.findCase(caseId).map(CaseRow::preventive).orElse(false);
        repo.addReportToCase(caseId, priority.level(), reason, due, preventive, csa);
        long reportId = repo.insertReport(caseId, source, reporterId, name, email, reason, details, csa, lowTrust, ipHash);
        if (preventive && !wasPreventive) {
            targets.preventive(t.type(), t.key());
            repo.insertDecision(caseId, "PREVENTIVE", ModerationAction.HIDE, ReportReason.CRIANCA_EM_RISCO,
                    "Retirada preventiva por denúncia de risco a criança ou adolescente (ECA Digital e Decreto 12.880, "
                            + "arts. 41 a 44), até a revisão humana.", true, null);
        }
        CaseRow c = repo.findCase(caseId).orElseThrow();
        String message = "Recebemos a sua denúncia. Uma pessoa da moderação analisa até " + DAY.format(c.dueAt())
                + ", e você acompanha o resultado pelo protocolo.";
        if (preventive) {
            message = "Recebemos a sua denúncia. O conteúdo saiu do ar na hora enquanto a moderação analisa. Se uma "
                    + "criança corre risco agora, ligue 190 ou disque 100.";
        }
        return new ReceiptView(protocol(reportId, now), "RECEBIDA", message, now, c.dueAt());
    }

    private boolean lowTrust(long reporterId) {
        long[] track = repo.reporterTrack(reporterId, clock.instant().minus(policy.lowTrustWindow()));
        return policy.lowTrust(track[0], track[1]);
    }

    private static TargetType type(String code, List<String> errors) {
        TargetType t = TargetType.fromCode(code).orElse(null);
        if (t == null) {
            errors.add("Tipo de conteúdo: POST, COMMENT, REVIEW, PROFILE ou ESTABLISHMENT.");
        }
        return t;
    }

    private static ReportReason reason(String code, TargetType type, List<String> errors) {
        ReportReason r = ReportReason.fromCode(code).orElse(null);
        if (r == null) {
            errors.add("Escolha um motivo da lista.");
        } else if (type != null && !r.appliesTo(type)) {
            errors.add("\"" + r.getLabel() + "\" não vale para " + type.getLabel().toLowerCase(Locale.ROOT) + ".");
        }
        return r;
    }

    private static String details(String text, ReportReason reason, List<String> errors) {
        String d = text == null ? null : text.strip();
        if (d != null && d.isEmpty()) {
            d = null;
        }
        if (d != null && d.length() > MAX_DETAILS) {
            errors.add("O texto vai até " + MAX_DETAILS + " caracteres.");
        }
        if (reason == ReportReason.OUTRO && (d == null || d.length() < 10)) {
            errors.add("Em \"Outro motivo\", conte em poucas palavras o que aconteceu.");
        }
        return d;
    }

    /** "DEN-2026-000042": ano e número da denúncia. */
    public static String protocol(long reportId, Instant createdAt) {
        int year = createdAt.atZone(ZoneId.of("America/Sao_Paulo")).getYear();
        return "DEN-%d-%06d".formatted(year, reportId);
    }
}
