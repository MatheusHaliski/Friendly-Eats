package com.friendlyeats.api;

import com.friendlyeats.application.post.PostDrafts.PostDraft;
import com.friendlyeats.application.post.PostPublishingService;
import com.friendlyeats.application.post.PostViews.PostDetailView;
import com.friendlyeats.application.post.PostVocabulary;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

/**
 * Publicar, editar, arquivar, restaurar e apagar o próprio post de comida (RF9), e os vocabulários do formulário. Toda
 * escrita exige sessão e {@code X-XSRF-TOKEN} (RF4).
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostPublishingController {

    private final PostPublishingService publishing;
    private final PostVocabulary vocabulary;
    private final ViewerResolver viewer;

    public PostPublishingController(PostPublishingService publishing, PostVocabulary vocabulary,
                                    ViewerResolver viewer) {
        this.publishing = publishing;
        this.vocabulary = vocabulary;
        this.viewer = viewer;
    }

    /** Público; com sessão traz também os tipos que a conta pode publicar. */
    @GetMapping("/vocabulary")
    public PostVocabulary.Vocabulary vocabulary() {
        return vocabulary.get(viewer.viewerId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailView create(@RequestBody PostDraft body) {
        return publishing.create(viewer.requireViewer(), body);
    }

    /** Chave ausente mantém o valor; {@code null} limpa ({@code "background": null} volta à moldura padrão). */
    @PatchMapping("/{postId}")
    public PostDetailView update(@PathVariable UUID postId, @RequestBody Map<String, Object> patch) {
        return publishing.update(viewer.requireViewer(), postId, patch);
    }

    @PostMapping("/{postId}/archive")
    public PostDetailView archive(@PathVariable UUID postId) {
        return publishing.archive(viewer.requireViewer(), postId);
    }

    @PostMapping("/{postId}/restore")
    public PostDetailView restore(@PathVariable UUID postId) {
        return publishing.restore(viewer.requireViewer(), postId);
    }

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID postId) {
        publishing.remove(viewer.requireViewer(), postId);
    }
}
