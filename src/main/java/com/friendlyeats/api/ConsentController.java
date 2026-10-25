package com.friendlyeats.api;

import com.friendlyeats.application.consent.ConsentService;
import com.friendlyeats.application.consent.ConsentService.ConsentList;
import com.friendlyeats.application.consent.ConsentService.ConsentView;
import com.friendlyeats.security.AppSecrets;
import com.friendlyeats.security.ClientInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Consentimentos da própria conta (RF3): Configurações → Privacidade. Uma chave por finalidade, todas desligadas
 * por padrão; desligar é tão fácil quanto ligar (LGPD Art. 8º, §5º).
 */
@RestController
@RequestMapping("/api/v1/me/consents")
public class ConsentController {

    private final ViewerResolver viewer;
    private final ConsentService consents;
    private final AppSecrets secrets;

    public ConsentController(ViewerResolver viewer, ConsentService consents, AppSecrets secrets) {
        this.viewer = viewer;
        this.consents = consents;
        this.secrets = secrets;
    }

    @GetMapping
    public ConsentList list() {
        return consents.list(viewer.requireViewer());
    }

    public record ConsentChange(Boolean granted) {
    }

    @PutMapping("/{purpose}")
    public ConsentView set(@PathVariable String purpose, @RequestBody ConsentChange body, HttpServletRequest request) {
        return consents.set(viewer.requireViewer(), purpose, body.granted(), ClientInfo.from(request, secrets));
    }
}
