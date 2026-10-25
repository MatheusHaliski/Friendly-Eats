package com.friendlyeats.api;

import com.friendlyeats.application.establishment.EstablishmentDrafts.ClassificationDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.EstablishmentDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.ReviewDraft;
import com.friendlyeats.application.establishment.EstablishmentSearch;
import com.friendlyeats.application.establishment.EstablishmentService;
import com.friendlyeats.application.establishment.EstablishmentViews.EstablishmentView;
import com.friendlyeats.application.establishment.EstablishmentViews.ReviewPage;
import com.friendlyeats.application.establishment.EstablishmentViews.ReviewView;
import com.friendlyeats.application.establishment.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Busca (RF10), estabelecimentos (RF6), classificação (RF7) e avaliações com estrelas (RF8). Leitura pública (com a regra de 18+
 * para lugares de bebida alcoólica); escrita exige sessão e {@code X-XSRF-TOKEN}.
 */
@RestController
@RequestMapping("/api/v1")
public class EstablishmentController {

    private final ViewerResolver viewer;
    private final EstablishmentService establishments;
    private final ReviewService reviews;
    private final EstablishmentSearch search;

    public EstablishmentController(ViewerResolver viewer, EstablishmentService establishments, ReviewService reviews,
                                   EstablishmentSearch search) {
        this.viewer = viewer;
        this.establishments = establishments;
        this.reviews = reviews;
        this.search = search;
    }

    /** Busca e filtros (RF10). Parâmetros repetíveis aceitam também lista separada por vírgula. */
    @GetMapping("/establishments")
    public EstablishmentSearch.Page search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) List<String> chip,
                                           @RequestParam(required = false) List<String> cuisine,
                                           @RequestParam(required = false) List<String> format,
                                           @RequestParam(required = false) List<String> type,
                                           @RequestParam(required = false) List<String> group,
                                           @RequestParam(required = false) List<String> price,
                                           @RequestParam(required = false) Double minRating,
                                           @RequestParam(required = false) List<String> dietary,
                                           @RequestParam(required = false) Double lat,
                                           @RequestParam(required = false) Double lng,
                                           @RequestParam(required = false) Double radiusKm,
                                           @RequestParam(required = false) String municipality,
                                           @RequestParam(required = false) String sort,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return search.search(viewer.viewerId(), new EstablishmentSearch.Query(q, chip, cuisine, format, type, group, price,
                minRating, dietary, lat, lng, radiusKm, municipality, sort, page, size));
    }

    @PostMapping("/establishments")
    @ResponseStatus(HttpStatus.CREATED)
    public EstablishmentView create(@RequestBody EstablishmentDraft body) {
        return establishments.create(viewer.requireViewer(), body);
    }

    @GetMapping("/establishments/{id}")
    public EstablishmentView get(@PathVariable long id) {
        return establishments.get(viewer.viewerId(), id);
    }

    @PatchMapping("/establishments/{id}")
    public EstablishmentView update(@PathVariable long id, @RequestBody EstablishmentDraft body) {
        return establishments.update(viewer.requireViewer(), id, body);
    }

    @PutMapping("/establishments/{id}/classification")
    public EstablishmentView classify(@PathVariable long id, @RequestBody ClassificationDraft body) {
        return establishments.classify(viewer.requireViewer(), id, body);
    }

    @GetMapping("/me/establishments")
    public List<EstablishmentView> mine() {
        return establishments.mine(viewer.requireViewer());
    }

    // ------------------------------------------------------------ avaliações (RF8)

    @GetMapping("/establishments/{id}/reviews")
    public ReviewPage reviews(@PathVariable long id, @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return reviews.list(viewer.viewerId(), id, page, size);
    }

    @GetMapping("/establishments/{id}/reviews/me")
    public ResponseEntity<ReviewView> myReview(@PathVariable long id) {
        return ResponseEntity.of(reviews.mine(viewer.requireViewer(), id));
    }

    /** Cria (201) ou substitui (200) a sua avaliação; a versão anterior fica no histórico. */
    @PutMapping("/establishments/{id}/reviews/me")
    public ResponseEntity<ReviewView> review(@PathVariable long id, @RequestBody ReviewDraft body) {
        ReviewService.Saved saved = reviews.upsertMine(viewer.requireViewer(), id, body);
        return ResponseEntity.status(saved.created() ? HttpStatus.CREATED : HttpStatus.OK).body(saved.review());
    }

    @DeleteMapping("/establishments/{id}/reviews/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReview(@PathVariable long id) {
        reviews.deleteMine(viewer.requireViewer(), id);
    }
}
