package com.friendlyeats.application.moderation;

import com.friendlyeats.application.establishment.EstablishmentViews.Coded;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Respostas da moderação (RF13): denúncia e recibo, fila, decisão, recurso, integridade e relatório. */
public final class ModerationViews {

    private ModerationViews() {
    }

    // ------------------------------------------------------------ pedidos

    /** Denúncia de quem tem conta. {@code childSexualAbuse}: indício de abuso sexual infantil (só com CRIANCA_EM_RISCO). */
    public record ReportRequest(String targetType, String targetId, String reason, String details,
                                Boolean childSexualAbuse) {
    }

    /** Formulário público (não usuário): nome e e-mail são obrigatórios (ECA Digital, art. 29, §2º). */
    public record PublicReportRequest(String name, String email, String targetType, String targetId, String reason,
                                      String details, Boolean childSexualAbuse) {
    }

    /** Contestação de avaliação pelo dono do lugar (RC-SEG-08). */
    public record ContestRequest(String reason, String details) {
    }

    public record DecisionRequest(String action, String policyReason, String statement) {
    }

    /** {@code outcome}: MAINTAINED ou REVERSED; {@code action} só quando revertido (padrão: KEEP). */
    public record AppealDecisionRequest(String outcome, String action, String policyReason, String statement) {
    }

    public record AppealRequest(String reason) {
    }

    public record AuthorityReferralRequest(String reference) {
    }

    public record AlertRequest(String kind, String message, Integer freezeDays) {
    }

    // ------------------------------------------------------------ catálogo e recibo

    public record ReasonView(String code, String label, String description, String priority, String priorityLabel,
                             List<String> targets, boolean ownerContest) {
    }

    /** Recibo: o número do protocolo e o que acontece agora. */
    public record ReceiptView(String protocol, String status, String message, Instant createdAt, Instant reviewBy) {
    }

    // ------------------------------------------------------------ alvo

    /** {@code path}: onde o alvo abre no app web ({@code null} quando não abre mais). */
    public record TargetView(String type, String typeLabel, String id, String path, String excerpt) {
    }

    // ------------------------------------------------------------ fila do moderador

    public record PriorityView(String code, String label, int level) {
    }

    public record CaseSummaryView(long id, String protocol, TargetView target, Coded topReason, PriorityView priority,
                                  Coded status, int reportCount, boolean preventive, String authorityReferral,
                                  Instant dueAt, boolean overdue, Instant createdAt, Instant updatedAt) {
    }

    public record CasePageView(List<CaseSummaryView> items, int page, int size, long total) {
    }

    public record ReportView(long id, String protocol, Coded source, Coded reason, String details, String reporter,
                             boolean lowTrust, boolean childSexualAbuse, String status, Instant createdAt,
                             Instant resolvedAt) {
    }

    public record DecisionView(long id, String stage, Coded action, Coded policy, String statement, boolean automated,
                               String origin, Long moderatorId, Instant createdAt) {
    }

    public record AppealView(long id, String role, String reason, String status, long decisionId,
                             Long resultDecisionId, Instant createdAt, Instant resolvedAt) {
    }

    public record SignalView(String code, String label, String detail, Instant createdAt) {
    }

    public record CaseDetailView(CaseSummaryView summary, Map<String, Object> snapshot, String currentState,
                                 Long targetOwnerId, Long establishmentId, String authorityReference,
                                 Instant authoritySentAt, List<ReportView> reports, List<SignalView> signals,
                                 List<DecisionView> decisions, List<AppealView> appeals, List<Coded> allowedActions,
                                 boolean viewerCanDecide, String viewerBlockedReason) {
    }

    // ------------------------------------------------------------ quem denunciou e quem foi afetado

    public record MyReportView(String protocol, TargetView target, Coded reason, String status, String statusLabel,
                               String outcome, String outcomeLabel, Instant createdAt, Instant resolvedAt,
                               long caseId, boolean canAppeal, Instant appealDeadline, String appealStatus) {
    }

    /**
     * Aviso ao autor (ECA Digital, art. 30): o que aconteceu, o motivo e a fundamentação, a origem (humana ou
     * automática), como recorrer e os prazos. Nunca diz quem denunciou.
     */
    public record MyDecisionView(long caseId, String protocol, TargetView target, DecisionView decision,
                                 boolean canAppeal, Instant appealDeadline, int appealResponseDays, String howToAppeal,
                                 AppealView appeal, DecisionView appealResult) {
    }

    // ------------------------------------------------------------ integridade (página do lugar)

    public record AlertView(long id, Coded kind, String message, boolean freezeReviews, Instant startsAt,
                            Instant endsAt, Instant liftedAt, boolean automated, boolean active) {
    }

    public record IntegrityView(long establishmentId, boolean frozen, Instant frozenUntil, List<AlertView> active,
                                List<AlertView> history, String howWeVerify) {
    }

    // ------------------------------------------------------------ relatório

    public record CountView(String code, String label, long count) {
    }

    public record StatsView(Instant from, Instant to, long openCases, long inAppeal, long overdue,
                            List<CountView> reportsByReason, List<CountView> reportsBySource,
                            List<CountView> decisionsByAction, List<CountView> decisionsByPolicy,
                            List<CountView> appeals, List<CountView> authorityReferrals) {
    }

    public record RolesView(boolean moderator) {
    }
}
