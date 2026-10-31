package com.friendlyeats.api;

import com.friendlyeats.application.age.AgeVerificationService;
import com.friendlyeats.application.age.AgeVerificationService.AgeVerificationView;
import com.friendlyeats.application.age.AgeVerificationService.AppealView;
import com.friendlyeats.application.age.AgeVerificationService.QueueItem;
import com.friendlyeats.application.age.AgeVerificationService.StartView;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Verificação de idade (RF17): status, começar no fornecedor, concluir (o servidor pergunta o resultado ao
 * fornecedor) e contestar; e a fila de contestações para quem revisa ({@code friendly-eats.age-verification.reviewers}).
 */
@RestController
public class AgeVerificationController {

    private final ViewerResolver viewer;
    private final AgeVerificationService ages;

    public AgeVerificationController(ViewerResolver viewer, AgeVerificationService ages) {
        this.viewer = viewer;
        this.ages = ages;
    }

    @GetMapping("/api/v1/me/age-verification")
    public AgeVerificationView status() {
        return ages.view(viewer.requireViewer());
    }

    @PostMapping("/api/v1/me/age-verification/start")
    public StartView start() {
        return ages.start(viewer.requireViewer());
    }

    /** {@code params}: o que o fornecedor devolveu na volta (no fake, {@code simulate}). */
    public record CompleteRequest(String reference, Map<String, String> params) {
    }

    @PostMapping("/api/v1/me/age-verification/complete")
    public AgeVerificationView complete(@RequestBody CompleteRequest body) {
        return ages.complete(viewer.requireViewer(), body.reference(), body.params());
    }

    public record AppealRequest(String reason) {
    }

    @PostMapping("/api/v1/me/age-verification/appeal")
    @ResponseStatus(HttpStatus.CREATED)
    public AppealView appeal(@RequestBody AppealRequest body) {
        return ages.appeal(viewer.requireViewer(), body.reason());
    }

    // ------------------------------------------------------------ revisão humana (Decreto 12.880, art. 27)

    @GetMapping("/api/v1/review/age-appeals")
    public List<QueueItem> queue() {
        return ages.queue(viewer.requireViewer());
    }

    public record Decision(Boolean accept, String note) {
    }

    @PostMapping("/api/v1/review/age-appeals/{id}")
    public AppealView decide(@PathVariable long id, @RequestBody Decision body) {
        if (body.accept() == null) {
            throw new com.friendlyeats.application.account.AccountExceptions.Invalid(
                    "Informe accept: true para aceitar ou false para recusar.");
        }
        return ages.decide(viewer.requireViewer(), id, body.accept(), body.note());
    }
}
