package com.friendlyeats.api;

import com.friendlyeats.application.guardian.GuardianService;
import com.friendlyeats.application.guardian.GuardianService.MinorLinkView;
import com.friendlyeats.application.guardian.GuardianService.RequestView;
import com.friendlyeats.application.guardian.GuardianService.SupervisedView;
import com.friendlyeats.application.guardian.GuardianService.SupervisionView;
import com.friendlyeats.application.account.AccountExceptions;
import com.friendlyeats.security.AppSecrets;
import com.friendlyeats.security.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Conta de menor vinculada à do responsável (RF17): o lado do menor em {@code /api/v1/me/guardian} e o painel de
 * supervisão em {@code /api/v1/guardian}. Tudo exige sessão; escrita exige {@code X-XSRF-TOKEN}.
 */
@RestController
public class GuardianController {

    private final ViewerResolver viewer;
    private final GuardianService guardians;
    private final AppSecrets secrets;

    public GuardianController(ViewerResolver viewer, GuardianService guardians, AppSecrets secrets) {
        this.viewer = viewer;
        this.guardians = guardians;
        this.secrets = secrets;
    }

    // ------------------------------------------------------------ menor

    @GetMapping("/api/v1/me/guardian")
    public MinorLinkView status() {
        return guardians.minorView(viewer.requireViewer());
    }

    /** {@code guardianEmail} vazio reenvia o convite pendente; outro e-mail troca o convite. */
    public record InviteRequest(String guardianEmail) {
    }

    @PostMapping("/api/v1/me/guardian/invite")
    public MinorLinkView invite(@RequestBody(required = false) InviteRequest body) {
        return guardians.invite(viewer.requireViewer(), body == null ? null : body.guardianEmail());
    }

    public record ProtectionRequest(String protection) {
    }

    @PostMapping("/api/v1/me/guardian/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView request(@RequestBody ProtectionRequest body) {
        return guardians.request(viewer.requireViewer(), body.protection());
    }

    @DeleteMapping("/api/v1/me/guardian")
    public MinorLinkView unlink(HttpServletRequest request) {
        return guardians.unlinkByMinor(viewer.requireViewer(), ClientInfo.from(request, secrets));
    }

    // ------------------------------------------------------------ responsável

    public record AcceptRequest(String token) {
    }

    @PostMapping("/api/v1/guardian/links/accept")
    public SupervisedView accept(@RequestBody AcceptRequest body) {
        return guardians.accept(viewer.requireViewer(), body.token());
    }

    @GetMapping("/api/v1/guardian/minors")
    public List<SupervisedView> minors() {
        return guardians.minors(viewer.requireViewer());
    }

    @GetMapping("/api/v1/guardian/minors/{minorId}")
    public SupervisionView panel(@PathVariable long minorId) {
        return guardians.panel(viewer.requireViewer(), minorId);
    }

    public record DecisionRequest(Boolean approve) {
    }

    @PostMapping("/api/v1/guardian/minors/{minorId}/requests/{requestId}")
    public SupervisionView decide(@PathVariable long minorId, @PathVariable long requestId,
                                  @RequestBody DecisionRequest body) {
        if (body.approve() == null) {
            throw new AccountExceptions.Invalid("Informe approve: true para aprovar ou false para negar.");
        }
        return guardians.decide(viewer.requireViewer(), minorId, requestId, body.approve());
    }

    public record AllowRequest(Boolean allowed) {
    }

    @PutMapping("/api/v1/guardian/minors/{minorId}/protections/{protection}")
    public SupervisionView protection(@PathVariable long minorId, @PathVariable String protection,
                                      @RequestBody AllowRequest body) {
        if (body.allowed() == null) {
            throw new AccountExceptions.Invalid("Informe allowed: true para desligar a proteção ou false para religar.");
        }
        return guardians.setProtection(viewer.requireViewer(), minorId, protection, body.allowed());
    }

    public record ConsentRequest(Boolean granted) {
    }

    @PutMapping("/api/v1/guardian/minors/{minorId}/sensitive-consent")
    public SupervisionView sensitiveConsent(@PathVariable long minorId, @RequestBody ConsentRequest body,
                                            HttpServletRequest request) {
        if (body.granted() == null) {
            throw new AccountExceptions.Invalid("Informe granted: true para consentir ou false para revogar.");
        }
        return guardians.setSensitiveConsent(viewer.requireViewer(), minorId, body.granted(),
                ClientInfo.from(request, secrets));
    }

    @DeleteMapping("/api/v1/guardian/minors/{minorId}")
    public ResponseEntity<Void> unlinkMinor(@PathVariable long minorId, HttpServletRequest request) {
        guardians.unlinkByGuardian(viewer.requireViewer(), minorId, ClientInfo.from(request, secrets));
        return ResponseEntity.noContent().build();
    }
}
