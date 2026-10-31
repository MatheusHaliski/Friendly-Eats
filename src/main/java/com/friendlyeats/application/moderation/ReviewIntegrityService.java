package com.friendlyeats.application.moderation;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentService;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.moderation.ModerationTargets.Target;
import com.friendlyeats.application.moderation.ModerationViews.AlertRequest;
import com.friendlyeats.application.moderation.ModerationViews.AlertView;
import com.friendlyeats.application.moderation.ModerationViews.IntegrityView;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.moderation.AlertKind;
import com.friendlyeats.domain.moderation.ModerationAction;
import com.friendlyeats.domain.moderation.ModerationPriority;
import com.friendlyeats.domain.moderation.ReportReason;
import com.friendlyeats.domain.moderation.ReportSource;
import com.friendlyeats.domain.moderation.ReviewSignal;
import com.friendlyeats.domain.moderation.TargetType;
import com.friendlyeats.infrastructure.persistence.postgres.IntegrityRepository;
import com.friendlyeats.infrastructure.persistence.postgres.IntegrityRepository.AlertRow;
import com.friendlyeats.infrastructure.persistence.postgres.ModerationRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Integridade das avaliações (RF13 · RC-SEG-04 e 05). Na criação de uma avaliação (RF8) procura sinais de fraude:
 * <ul>
 *   <li>rajada de contas novas no mesmo lugar: a partir de {@code burst-threshold} avaliações na janela, todas vão para
 *       {@code UNDER_REVIEW} (fora da lista e da nota) e entram na fila; a partir de {@code freeze-threshold}, novas
 *       avaliações do lugar ficam congeladas por {@code freeze-days} com o alerta "Atividade fora do padrão";</li>
 *   <li>mesma origem (HMAC do IP) em contas diferentes no mesmo lugar: as envolvidas vão para análise;</li>
 *   <li>vínculo declarado (cortesia, convite ou relação): só registra o sinal; o peso já cai pelo {@code trust}.</li>
 * </ul>
 * A decisão automática é só preventiva: uma pessoa da moderação decide (LGPD, art. 20, revisão por política da casa).
 */
@Service
public class ReviewIntegrityService {

    static final ZoneId BRAZIL = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).withZone(BRAZIL);
    static final String HOW_WE_VERIFY = "As avaliações entram no ar sem revisão prévia. Um filtro procura sinais de "
            + "fraude, como muitas avaliações de contas novas em pouco tempo, contas diferentes vindas da mesma origem e "
            + "vínculo declarado com o lugar. As suspeitas saem da nota até uma pessoa da moderação decidir. Quem declara "
            + "cortesia ou convite pesa menos na nota, e quem declara vínculo com o lugar fica fora dela. O dono pode "
            + "contestar uma avaliação só pelos motivos da política, sem ver dados de quem avaliou.";

    private final IntegrityRepository integrity;
    private final ModerationRepository moderation;
    private final ModerationTargets targets;
    private final ModerationPolicy policy;
    private final ModerationRoles roles;
    private final EstablishmentService establishments;
    private final UserAccountRepository users;
    private final Clock clock;

    public ReviewIntegrityService(IntegrityRepository integrity, ModerationRepository moderation,
                                  ModerationTargets targets, ModerationPolicy policy, ModerationRoles roles,
                                  EstablishmentService establishments, UserAccountRepository users, Clock clock) {
        this.integrity = integrity;
        this.moderation = moderation;
        this.targets = targets;
        this.policy = policy;
        this.roles = roles;
        this.establishments = establishments;
        this.users = users;
        this.clock = clock;
    }

    // ------------------------------------------------------------ congelamento

    public Optional<Instant> frozenUntil(long establishmentId) {
        return integrity.frozenUntil(establishmentId, clock.instant());
    }

    /** Novas avaliações pausadas: {@code 409} com a data de fim. Quem já avaliou continua podendo editar. */
    public void requireNotFrozen(long establishmentId) {
        frozenUntil(establishmentId).ifPresent(until -> {
            throw new CommonExceptions.Conflict("Novas avaliações deste lugar estão pausadas até " + WHEN.format(until)
                    + ", por atividade fora do padrão. Quem já avaliou pode editar a própria avaliação.");
        });
    }

    // ------------------------------------------------------------ sinais

    /** Chamado pelo RF8 na mesma transação, logo depois de gravar uma avaliação nova. */
    @Transactional
    public void afterNewReview(Review review, Review.Disclosure disclosure) {
        long place = review.getEstablishmentId();
        if (disclosure == Review.Disclosure.COURTESY || disclosure == Review.Disclosure.INVITATION) {
            integrity.addSignal(review.getId(), ReviewSignal.INCENTIVE_DECLARED, disclosure.name());
        } else if (disclosure == Review.Disclosure.RELATIONSHIP) {
            integrity.addSignal(review.getId(), ReviewSignal.CONFLICT_DECLARED, null);
        }
        if (!policy.signalsEnabled()) {
            return;
        }
        Instant now = clock.instant();
        Set<Long> suspicious = new LinkedHashSet<>();

        List<Long> burst = integrity.recentFromNewAccounts(place, now.minus(policy.burstWindow()), policy.newAccountDays());
        if (burst.size() >= policy.burstThreshold()) {
            for (long id : burst) {
                if (integrity.addSignal(id, ReviewSignal.BURST_NEW_ACCOUNTS, burst.size() + " na janela")) {
                    suspicious.add(id);
                }
            }
            if (burst.size() >= policy.freezeThreshold() && frozenUntil(place).isEmpty()) {
                freezeAutomatically(place, now);
            }
        }
        if (review.getOriginHash() != null) {
            List<Long> same = integrity.sameOrigin(place, review.getId(), review.getAuthorId(), review.getOriginHash(),
                    now.minus(policy.sameOriginWindow()));
            if (!same.isEmpty()) {
                List<Long> all = new ArrayList<>(same);
                all.add(review.getId());
                for (long id : all) {
                    if (integrity.addSignal(id, ReviewSignal.SAME_ORIGIN, all.size() + " contas")) {
                        suspicious.add(id);
                    }
                }
            }
        }
        for (long id : suspicious) {
            putUnderReview(id, now);
        }
    }

    /** A avaliação sai da nota e entra na fila como caso do sistema, com a decisão preventiva registrada. */
    private void putUnderReview(long reviewId, Instant now) {
        Optional<Target> target = targets.current(TargetType.REVIEW, Long.toString(reviewId));
        if (target.isEmpty()) {
            return;
        }
        Target t = target.get();
        String signals = String.join("; ", integrity.signalsOf(reviewId).stream()
                .filter(s -> s.code().suspicious()).map(s -> s.code().getLabel()).toList());
        ModerationPriority priority = ReportReason.AVALIACAO_FALSA.priority();
        Instant due = now.plus(priority.sla());
        long caseId = moderation.openCase(t.type(), t.key(), t.ownerId(), t.establishmentId(), priority.level(),
                ReportReason.AVALIACAO_FALSA, t.snapshot(), due);
        boolean alreadyPreventive = moderation.findCase(caseId).map(ModerationRepository.CaseRow::preventive).orElse(false);
        moderation.addReportToCase(caseId, priority.level(), ReportReason.AVALIACAO_FALSA, due, true, false);
        moderation.insertReport(caseId, ReportSource.SYSTEM, null, null, null, ReportReason.AVALIACAO_FALSA,
                "Sinais automáticos: " + signals, false, false, null);
        if (!alreadyPreventive) {
            targets.preventive(TargetType.REVIEW, t.key());
            moderation.insertDecision(caseId, "PREVENTIVE", ModerationAction.HIDE, ReportReason.AVALIACAO_FALSA,
                    "Sinal automático de fraude (" + signals + "). A avaliação saiu da lista e da nota até uma pessoa "
                            + "da moderação revisar.", true, null);
        }
    }

    private void freezeAutomatically(long place, Instant now) {
        integrity.insertAlert(place, AlertKind.ATIVIDADE_SUSPEITA, AlertKind.ATIVIDADE_SUSPEITA.getDefaultMessage(),
                true, now, now.plus(Duration.ofDays(policy.freezeDays())), true, null);
        targets.current(TargetType.ESTABLISHMENT, Long.toString(place)).ifPresent(t -> {
            ModerationPriority priority = ModerationPriority.ALTA;
            Instant due = now.plus(priority.sla());
            long caseId = moderation.openCase(t.type(), t.key(), t.ownerId(), t.establishmentId(), priority.level(),
                    ReportReason.AVALIACAO_FALSA, t.snapshot(), due);
            moderation.addReportToCase(caseId, priority.level(), ReportReason.AVALIACAO_FALSA, due, false, false);
            moderation.insertReport(caseId, ReportSource.SYSTEM, null, null, null, ReportReason.AVALIACAO_FALSA,
                    "Pico de avaliações de contas novas: novas avaliações congeladas por " + policy.freezeDays()
                            + " dias. Revise o alerta e as avaliações em análise.", false, false, null);
        });
    }

    // ------------------------------------------------------------ página do lugar (público)

    @Transactional(readOnly = true)
    public IntegrityView view(Optional<Long> viewerId, long establishmentId) {
        establishments.requireVisible(establishmentId, viewerId.flatMap(users::findById));
        Instant now = clock.instant();
        List<AlertView> history = integrity.alertsOf(establishmentId).stream().map(a -> view(a, now)).toList();
        Optional<Instant> frozen = frozenUntil(establishmentId);
        return new IntegrityView(establishmentId, frozen.isPresent(), frozen.orElse(null),
                history.stream().filter(AlertView::active).toList(), history, HOW_WE_VERIFY);
    }

    // ------------------------------------------------------------ moderação

    @Transactional
    public AlertView createAlert(long moderatorId, long establishmentId, AlertRequest req) {
        roles.requireModerator(moderatorId);
        if (targets.current(TargetType.ESTABLISHMENT, Long.toString(establishmentId)).isEmpty()) {
            throw new CommonExceptions.NotFound("Estabelecimento não encontrado.");
        }
        List<String> errors = new ArrayList<>();
        AlertKind kind = req == null ? null : AlertKind.fromCode(req.kind()).orElse(null);
        if (kind == null) {
            errors.add("Escolha o tipo de alerta: AVALIACAO_PAGA, ATIVIDADE_SUSPEITA ou ATENCAO_PUBLICA.");
        }
        String message = req == null || req.message() == null || req.message().isBlank() ? null : req.message().strip();
        if (message != null && message.length() > 500) {
            errors.add("O texto do alerta vai até 500 caracteres.");
        }
        int days = req == null || req.freezeDays() == null ? 0 : req.freezeDays();
        if (days < 0 || days > 30) {
            errors.add("O congelamento vai de 0 a 30 dias.");
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        Instant now = clock.instant();
        long id = integrity.insertAlert(establishmentId, kind, message == null ? kind.getDefaultMessage() : message,
                days > 0, now, days > 0 ? now.plus(Duration.ofDays(days)) : null, false, moderatorId);
        return view(integrity.findAlert(id).orElseThrow(), now);
    }

    @Transactional
    public AlertView liftAlert(long moderatorId, long establishmentId, long alertId) {
        roles.requireModerator(moderatorId);
        Instant now = clock.instant();
        if (!integrity.liftAlert(establishmentId, alertId, now)) {
            throw new CommonExceptions.NotFound("Alerta não encontrado ou já encerrado.");
        }
        return view(integrity.findAlert(alertId).orElseThrow(), now);
    }

    public List<ModerationViews.SignalView> signals(long reviewId) {
        return integrity.signalsOf(reviewId).stream()
                .map(s -> new ModerationViews.SignalView(s.code().name(), s.code().getLabel(), s.detail(), s.createdAt()))
                .toList();
    }

    static AlertView view(AlertRow a, Instant now) {
        return new AlertView(a.id(), new Coded(a.kind().name(), a.kind().getLabel(), null), a.message(),
                a.freezeReviews(), a.startsAt(), a.endsAt(), a.liftedAt(), a.automated(), a.activeAt(now));
    }
}
