package com.friendlyeats.application.establishment;

import com.friendlyeats.application.account.AccountViews.CategoryView;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentDrafts.ReviewDraft;
import com.friendlyeats.application.establishment.EstablishmentRules.ReviewContent;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.establishment.EstablishmentViews.ReviewAuthorView;
import com.friendlyeats.application.establishment.EstablishmentViews.ReviewPage;
import com.friendlyeats.application.establishment.EstablishmentViews.ReviewView;
import com.friendlyeats.application.moderation.ReviewIntegrityService;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.model.ReviewRevision;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.StarRating;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRevisionRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Avaliação com estrelas (RF8). Uma por pessoa e estabelecimento: o PUT cria ou substitui (guardando a versão
 * anterior), o DELETE apaga a própria. Regras contra avaliação falsa já no código:
 * <ul>
 *   <li>só pessoas avaliam — contas de Estabelecimento, Varejo & Marca e Oficial não ({@code RULE_NO_INSIDER});</li>
 *   <li>o dono nunca avalia o próprio lugar;</li>
 *   <li>a conta precisa estar ativa (e-mail confirmado);</li>
 *   <li>a avaliação sai publicada, sem censura prévia (Marco Civil art. 19); a moderação vem depois (RF13): sinais de
 *       fraude na criação ({@link ReviewIntegrityService}) podem pô-la em análise, fora da lista e da nota;</li>
 *   <li>lugar com novas avaliações congeladas (pico suspeito, RF13) recusa avaliação nova com {@code 409};</li>
 *   <li>quem avalia pode declarar cortesia, convite ou vínculo com o lugar ({@code disclosure}), que muda o peso.</li>
 * </ul>
 */
@Service
public class ReviewService {

    /** Contas que falam em nome de uma organização: não avaliam lugares (conflito de interesse). */
    static final Set<UserCategoryEnum> ORGANIZATIONS =
            Set.of(UserCategoryEnum.ESTABELECIMENTO, UserCategoryEnum.VAREJO, UserCategoryEnum.OFICIAL);
    private static final int MAX_PAGE_SIZE = 50;

    public record Saved(ReviewView review, boolean created) {
    }

    private final ReviewRepository reviews;
    private final ReviewRevisionRepository revisions;
    private final UserAccountRepository users;
    private final EstablishmentService establishments;
    private final ReviewIntegrityService integrity;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, ReviewRevisionRepository revisions, UserAccountRepository users,
                         EstablishmentService establishments, ReviewIntegrityService integrity, Clock clock) {
        this.reviews = reviews;
        this.revisions = revisions;
        this.users = users;
        this.establishments = establishments;
        this.integrity = integrity;
        this.clock = clock;
    }

    /** {@code originHash}: HMAC do IP de quem escreve (ClientInfo), guardado só na criação para o sinal de mesma origem. */
    @Transactional
    public Saved upsertMine(long viewerId, long establishmentId, ReviewDraft draft, String originHash) {
        UserAccount author = users.findById(viewerId).orElseThrow(CommonExceptions.Unauthenticated::new);
        Establishment place = establishments.requireVisible(establishmentId, Optional.of(author));
        if (Objects.equals(place.getOwnerId(), viewerId)) {
            throw new CommonExceptions.Forbidden("Você não pode avaliar o próprio estabelecimento.");
        }
        if (ORGANIZATIONS.contains(author.getUserType())) {
            throw new CommonExceptions.Forbidden("Contas de organização não avaliam lugares. Avalie com a sua conta pessoal.");
        }
        if (author.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new CommonExceptions.Forbidden("Sua conta precisa estar ativa para avaliar. Confirme seu e-mail.");
        }
        List<String> errors = new ArrayList<>();
        ReviewContent c = EstablishmentRules.review(draft, errors);
        Review.Disclosure disclosure = disclosure(draft == null ? null : draft.disclosure(), errors);
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        Optional<Review> existing = reviews.findByEstablishmentIdAndAuthorId(establishmentId, viewerId);
        Review r = existing.orElseGet(() -> new Review(establishmentId, viewerId));
        if (existing.isPresent()) {
            if (existing.get().getStatus() == Review.Status.REMOVED) {
                throw new CommonExceptions.Conflict("Sua avaliação deste lugar foi removida pela moderação.");
            }
            revisions.save(ReviewRevision.of(r, clock.instant()));
        } else {
            integrity.requireNotFrozen(establishmentId);
            r.setOriginHash(originHash);
        }
        r.write(c.rating(), c.visitMode(), c.dimensions(), c.spentPerPersonCents(), c.body(), clock.instant());
        r.declare(disclosure);
        Review saved = reviews.saveAndFlush(r);
        if (existing.isEmpty()) {
            integrity.afterNewReview(saved, disclosure);
        }
        return new Saved(view(saved, author), existing.isEmpty());
    }

    /** Vínculo declarado (RF13); ausente = mantém. */
    static Review.Disclosure disclosure(String code, List<String> errors) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return Review.Disclosure.valueOf(code.strip());
        } catch (IllegalArgumentException e) {
            errors.add("Vínculo com o lugar: use NONE, COURTESY, INVITATION ou RELATIONSHIP.");
            return null;
        }
    }

    @Transactional
    public void deleteMine(long viewerId, long establishmentId) {
        Review r = reviews.findByEstablishmentIdAndAuthorId(establishmentId, viewerId)
                .orElseThrow(() -> new CommonExceptions.NotFound("Você não avaliou este lugar."));
        if (r.getStatus() == Review.Status.REMOVED) {
            // apagar e avaliar de novo seria um jeito de driblar a moderação
            throw new CommonExceptions.Conflict("Sua avaliação deste lugar foi removida pela moderação.");
        }
        reviews.delete(r);
    }

    @Transactional(readOnly = true)
    public Optional<ReviewView> mine(long viewerId, long establishmentId) {
        UserAccount author = users.findById(viewerId).orElseThrow(CommonExceptions.Unauthenticated::new);
        return reviews.findByEstablishmentIdAndAuthorId(establishmentId, viewerId).map(r -> view(r, author));
    }

    /** Lista pública, mais recentes primeiro, com a nota agregada. */
    @Transactional(readOnly = true)
    public ReviewPage list(Optional<Long> viewerId, long establishmentId, int page, int size) {
        Establishment place = establishments.requireVisible(establishmentId, viewerId.flatMap(users::findById));
        int s = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Page<Object[]> rows = reviews.pageCounting(place.getId(), AccountStatus.ACTIVE, PageRequest.of(Math.max(0, page), s));
        List<ReviewView> items = rows.getContent().stream()
                .map(row -> view((Review) row[0], (UserAccount) row[1])).toList();
        return new ReviewPage(items, rows.getNumber(), s, rows.getTotalElements(),
                establishments.rating(reviews.findCounting(place.getId(), AccountStatus.ACTIVE)));
    }

    private static ReviewView view(Review r, UserAccount author) {
        Map<String, Integer> dims = new LinkedHashMap<>();
        r.getDimensions().forEach((k, v) -> dims.put(k.name(), v));
        return new ReviewView(r.getId(), r.getRating(), StarRating.of(r.getRating()).getLabel(),
                new Coded(r.getVisitMode().name(), r.getVisitMode().getLabel(), null), dims,
                r.getSpentPerPersonCents(), r.getBody(), r.getTrust().name(), r.getEditCount() > 0,
                new ReviewAuthorView(author.getName(), author.getHandle(), CategoryView.of(author.getUserType())),
                r.getCreatedAt(), r.getUpdatedAt(), r.getStatus().name());
    }
}
