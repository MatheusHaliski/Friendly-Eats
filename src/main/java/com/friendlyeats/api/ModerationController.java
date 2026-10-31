package com.friendlyeats.api;

import com.friendlyeats.application.moderation.ModerationService;
import com.friendlyeats.application.moderation.ModerationViews.AlertRequest;
import com.friendlyeats.application.moderation.ModerationViews.AlertView;
import com.friendlyeats.application.moderation.ModerationViews.AppealDecisionRequest;
import com.friendlyeats.application.moderation.ModerationViews.AuthorityReferralRequest;
import com.friendlyeats.application.moderation.ModerationViews.CaseDetailView;
import com.friendlyeats.application.moderation.ModerationViews.CasePageView;
import com.friendlyeats.application.moderation.ModerationViews.DecisionRequest;
import com.friendlyeats.application.moderation.ModerationViews.StatsView;
import com.friendlyeats.application.moderation.ReviewIntegrityService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Área da moderação (RF13), só com {@code ROLE_MODERATOR} (SecurityConfig) e o papel conferido no banco a cada ação:
 * fila priorizada, detalhe com histórico, decisão motivada, recurso por outra pessoa, comunicação às autoridades,
 * alertas e congelamento na página do lugar e o relatório agregado.
 */
@RestController
@RequestMapping("/api/v1/moderation")
public class ModerationController {

    private final ViewerResolver viewer;
    private final ModerationService moderation;
    private final ReviewIntegrityService integrity;

    public ModerationController(ViewerResolver viewer, ModerationService moderation, ReviewIntegrityService integrity) {
        this.viewer = viewer;
        this.moderation = moderation;
        this.integrity = integrity;
    }

    /** {@code status}: OPEN (padrão), IN_APPEAL, DECIDED ou FINAL. */
    @GetMapping("/cases")
    public CasePageView queue(@RequestParam(required = false) String status,
                              @RequestParam(required = false) Integer page,
                              @RequestParam(required = false) Integer size) {
        return moderation.queue(viewer.requireViewer(), status, page, size);
    }

    @GetMapping("/cases/{id}")
    public CaseDetailView detail(@PathVariable long id) {
        return moderation.detail(viewer.requireViewer(), id);
    }

    @PostMapping("/cases/{id}/decision")
    public CaseDetailView decide(@PathVariable long id, @RequestBody DecisionRequest body) {
        return moderation.decide(viewer.requireViewer(), id, body);
    }

    @PostMapping("/cases/{id}/appeal-decision")
    public CaseDetailView decideAppeal(@PathVariable long id, @RequestBody AppealDecisionRequest body) {
        return moderation.decideAppeal(viewer.requireViewer(), id, body);
    }

    @PostMapping("/cases/{id}/authority-referral")
    public CaseDetailView authority(@PathVariable long id, @RequestBody AuthorityReferralRequest body) {
        return moderation.recordAuthorityReferral(viewer.requireViewer(), id, body);
    }

    @PostMapping("/establishments/{id}/alerts")
    @ResponseStatus(HttpStatus.CREATED)
    public AlertView createAlert(@PathVariable long id, @RequestBody AlertRequest body) {
        return integrity.createAlert(viewer.requireViewer(), id, body);
    }

    @PostMapping("/establishments/{id}/alerts/{alertId}/lift")
    public AlertView liftAlert(@PathVariable long id, @PathVariable long alertId) {
        return integrity.liftAlert(viewer.requireViewer(), id, alertId);
    }

    /** Contadores agregados por motivo, origem, decisão e recurso (base do relatório de transparência). */
    @GetMapping("/stats")
    public StatsView stats(@RequestParam(required = false) Integer days) {
        return moderation.stats(viewer.requireViewer(), days);
    }
}
