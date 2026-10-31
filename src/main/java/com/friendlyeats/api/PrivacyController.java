package com.friendlyeats.api;

import com.friendlyeats.application.account.StepUp;
import com.friendlyeats.application.privacy.DataExportService;
import com.friendlyeats.application.privacy.DataExportService.ExportView;
import com.friendlyeats.application.privacy.PrivacyContact;
import com.friendlyeats.application.privacy.PrivacyContact.PrivacyContactView;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.security.AppSecrets;
import com.friendlyeats.security.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/**
 * Central do titular (RF18): o contato público do encarregado e a exportação dos dados ("Seus dados → Exportar").
 * Registros de acesso e incidentes não têm endpoint: são serviços internos.
 */
@RestController
public class PrivacyController {

    private final ViewerResolver viewer;
    private final PrivacyContact contact;
    private final DataExportService exports;
    private final StepUp stepUp;
    private final AppSecrets secrets;

    public PrivacyController(ViewerResolver viewer, PrivacyContact contact, DataExportService exports, StepUp stepUp,
                             AppSecrets secrets) {
        this.viewer = viewer;
        this.contact = contact;
        this.exports = exports;
        this.stepUp = stepUp;
        this.secrets = secrets;
    }

    /** Público (LGPD, art. 41, §1º): identidade e contato do encarregado, direitos e prazos. */
    @GetMapping("/api/v1/legal/privacy-contact")
    public ResponseEntity<PrivacyContactView> privacyContact() {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(contact.view());
    }

    @GetMapping("/api/v1/me/data-exports")
    public List<ExportView> list() {
        return exports.list(viewer.requireViewer());
    }

    /** Modo sudo: senha atual (+ código, com 2FA). */
    public record ExportRequest(String password, String code) {
    }

    @PostMapping("/api/v1/me/data-exports")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ExportView request(@RequestBody ExportRequest body, HttpServletRequest request) {
        UserAccount user = stepUp.confirm(viewer.requireViewer(), body.password(), body.code(),
                ClientInfo.from(request, secrets).ipHash());
        return exports.request(user);
    }
}
