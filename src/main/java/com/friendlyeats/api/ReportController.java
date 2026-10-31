package com.friendlyeats.api;

import com.friendlyeats.application.moderation.ModerationRoles;
import com.friendlyeats.application.moderation.ModerationService;
import com.friendlyeats.application.moderation.ModerationViews.AppealRequest;
import com.friendlyeats.application.moderation.ModerationViews.AppealView;
import com.friendlyeats.application.moderation.ModerationViews.ContestRequest;
import com.friendlyeats.application.moderation.ModerationViews.IntegrityView;
import com.friendlyeats.application.moderation.ModerationViews.MyDecisionView;
import com.friendlyeats.application.moderation.ModerationViews.MyReportView;
import com.friendlyeats.application.moderation.ModerationViews.PublicReportRequest;
import com.friendlyeats.application.moderation.ModerationViews.ReasonView;
import com.friendlyeats.application.moderation.ModerationViews.ReceiptView;
import com.friendlyeats.application.moderation.ModerationViews.ReportRequest;
import com.friendlyeats.application.moderation.ModerationViews.RolesView;
import com.friendlyeats.application.moderation.ReportService;
import com.friendlyeats.application.moderation.ReviewIntegrityService;
import com.friendlyeats.security.AppSecrets;
import com.friendlyeats.security.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Lado de quem usa o app na moderação (RF13): denunciar (com conta ou pelo formulário público), contestar uma avaliação
 * como dono, acompanhar as próprias denúncias, ver e contestar as decisões sobre o próprio conteúdo, e a integridade
 * pública da página do lugar (alertas ao consumidor e congelamento).
 */
@RestController
@RequestMapping("/api/v1")
public class ReportController {

    private final ViewerResolver viewer;
    private final ReportService reports;
    private final ModerationService moderation;
    private final ReviewIntegrityService integrity;
    private final ModerationRoles roles;
    private final AppSecrets secrets;

    public ReportController(ViewerResolver viewer, ReportService reports, ModerationService moderation,
                            ReviewIntegrityService integrity, ModerationRoles roles, AppSecrets secrets) {
        this.viewer = viewer;
        this.reports = reports;
        this.moderation = moderation;
        this.integrity = integrity;
        this.roles = roles;
        this.secrets = secrets;
    }

    /** Motivos fechados com rótulo e onde valem (público). */
    @GetMapping("/reports/reasons")
    public List<ReasonView> reasons() {
        return reports.reasons();
    }

    /** Denúncia com conta: 201 com o protocolo; 409 se já há uma aberta sua para o mesmo alvo; 429 no limite. */
    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptView report(@RequestBody ReportRequest body) {
        return reports.report(viewer.requireViewer(), body);
    }

    /** Canal para não usuários (STF, Temas 987 e 533, item 9): nome e e-mail obrigatórios, limite por origem. */
    @PostMapping("/reports/public")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptView publicReport(@RequestBody PublicReportRequest body, HttpServletRequest request) {
        return reports.publicReport(body, ClientInfo.from(request, secrets).ipHash());
    }

    /** Contestação de avaliação pelo dono do lugar (RC-SEG-08): a avaliação fica no ar até a decisão. */
    @PostMapping("/establishments/{establishmentId}/reviews/{reviewId}/contest")
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptView contest(@PathVariable long establishmentId, @PathVariable long reviewId,
                               @RequestBody ContestRequest body) {
        return reports.contest(viewer.requireViewer(), establishmentId, reviewId, body);
    }

    /** Alertas ao consumidor, congelamento e "como verificamos as avaliações" (público, com as regras da página). */
    @GetMapping("/establishments/{id}/integrity")
    public IntegrityView integrity(@PathVariable long id) {
        return integrity.view(viewer.viewerId(), id);
    }

    @GetMapping("/me/reports")
    public List<MyReportView> myReports() {
        return reports.mine(viewer.requireViewer());
    }

    /** Decisões com medida sobre o meu conteúdo (ECA Digital, art. 30). */
    @GetMapping("/me/moderation/decisions")
    public List<MyDecisionView> myDecisions() {
        return moderation.myDecisions(viewer.requireViewer());
    }

    /** Recurso: do autor contra a medida, ou de quem denunciou contra o arquivamento. */
    @PostMapping("/me/moderation/cases/{caseId}/appeal")
    @ResponseStatus(HttpStatus.CREATED)
    public AppealView appeal(@PathVariable long caseId, @RequestBody AppealRequest body) {
        return moderation.appeal(viewer.requireViewer(), caseId, body);
    }

    /** Papéis da conta (o app mostra "Moderação" só para moderador). */
    @GetMapping("/me/roles")
    public RolesView roles() {
        return new RolesView(roles.isModerator(viewer.requireViewer()));
    }
}
