package com.friendlyeats.api;

import com.friendlyeats.application.safety.SafetyService;
import com.friendlyeats.application.safety.SafetyViews.RelationView;
import com.friendlyeats.application.safety.SafetyViews.SafetyPatch;
import com.friendlyeats.application.safety.SafetyViews.SafetyView;
import com.friendlyeats.application.safety.SafetyViews.WordsRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Segurança entre usuários (RF19): bloquear, silenciar e palavras filtradas. PUT e DELETE idempotentes. */
@RestController
@RequestMapping("/api/v1/me")
public class SafetyController {

    private final ViewerResolver viewer;
    private final SafetyService safety;

    public SafetyController(ViewerResolver viewer, SafetyService safety) {
        this.viewer = viewer;
        this.safety = safety;
    }

    @GetMapping("/safety")
    public SafetyView mine() {
        return safety.mine(viewer.requireViewer());
    }

    /** Liga ou desliga o filtro padrão de ofensas (menor de 16: travado ligado, 403). */
    @PatchMapping("/safety")
    public SafetyView update(@RequestBody SafetyPatch body) {
        return safety.update(viewer.requireViewer(), body);
    }

    @PutMapping("/filtered-words")
    public SafetyView words(@RequestBody WordsRequest body) {
        return safety.replaceWords(viewer.requireViewer(), body);
    }

    @GetMapping("/relations/{userId}")
    public RelationView relation(@PathVariable long userId) {
        return safety.relation(viewer.requireViewer(), userId);
    }

    @PutMapping("/blocks/{userId}")
    public RelationView block(@PathVariable long userId) {
        return safety.block(viewer.requireViewer(), userId, true);
    }

    @DeleteMapping("/blocks/{userId}")
    public RelationView unblock(@PathVariable long userId) {
        return safety.block(viewer.requireViewer(), userId, false);
    }

    @PutMapping("/mutes/{userId}")
    public RelationView mute(@PathVariable long userId) {
        return safety.mute(viewer.requireViewer(), userId, true);
    }

    @DeleteMapping("/mutes/{userId}")
    public RelationView unmute(@PathVariable long userId) {
        return safety.mute(viewer.requireViewer(), userId, false);
    }
}
