package com.friendlyeats.application.safety;

import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.post.AuthorDirectory;
import com.friendlyeats.application.safety.SafetyViews.DefaultFilterView;
import com.friendlyeats.application.safety.SafetyViews.RelatedUserView;
import com.friendlyeats.application.safety.SafetyViews.RelationView;
import com.friendlyeats.application.safety.SafetyViews.SafetyPatch;
import com.friendlyeats.application.safety.SafetyViews.SafetyView;
import com.friendlyeats.application.safety.SafetyViews.WordsRequest;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.SafetyRepository;
import com.friendlyeats.infrastructure.persistence.postgres.SafetyRepository.Related;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Bloquear, silenciar e palavras filtradas (RF19). Tudo idempotente e sigiloso: nenhuma resposta conta a outra pessoa
 * que ela foi bloqueada ou silenciada. Desbloquear é possível a qualquer momento.
 */
@Service
public class SafetyService {

    private final SafetyRepository safety;
    private final SafetyGuard guard;
    private final AuthorDirectory authors;
    private final AgePolicy age;

    public SafetyService(SafetyRepository safety, SafetyGuard guard, AuthorDirectory authors, AgePolicy age) {
        this.safety = safety;
        this.guard = guard;
        this.authors = authors;
        this.age = age;
    }

    @Transactional(readOnly = true)
    public SafetyView mine(long viewerId) {
        UserAccount me = authors.find(viewerId).orElseThrow(CommonExceptions.Unauthenticated::new);
        boolean minor = age.protectedMinor(me.getUserDateOfBirth());
        return new SafetyView(related(safety.blockedBy(viewerId)), related(safety.mutedBy(viewerId)),
                safety.words(viewerId), new DefaultFilterView(guard.defaultFilterEnabled(me), minor,
                WordFilter.DEFAULT_WORDS.size()), WordFilter.MAX_WORDS);
    }

    @Transactional(readOnly = true)
    public RelationView relation(long viewerId, long otherId) {
        return new RelationView(otherId, safety.blocks(viewerId, otherId), safety.mutes(viewerId, otherId));
    }

    @Transactional
    public RelationView block(long viewerId, long otherId, boolean on) {
        requireOther(viewerId, otherId);
        if (on) {
            safety.block(viewerId, otherId);
        } else {
            safety.unblock(viewerId, otherId);
        }
        return relation(viewerId, otherId);
    }

    @Transactional
    public RelationView mute(long viewerId, long otherId, boolean on) {
        requireOther(viewerId, otherId);
        if (on) {
            safety.mute(viewerId, otherId);
        } else {
            safety.unmute(viewerId, otherId);
        }
        return relation(viewerId, otherId);
    }

    /** Troca a lista inteira (até {@value WordFilter#MAX_WORDS}); repetidas e variações de acento viram uma. */
    @Transactional
    public SafetyView replaceWords(long viewerId, WordsRequest req) {
        List<String> raw = req == null || req.words() == null ? List.of() : req.words();
        Set<String> words = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();
        for (String w : raw) {
            WordFilter.clean(w).ifPresentOrElse(words::add, () -> errors.add("\"" + (w == null ? "" : w.strip())
                    + "\": cada palavra ou expressão tem de " + WordFilter.MIN_LENGTH + " a " + WordFilter.MAX_LENGTH
                    + " letras."));
        }
        if (words.size() > WordFilter.MAX_WORDS) {
            errors.add("A lista vai até " + WordFilter.MAX_WORDS + " palavras.");
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        safety.replaceWords(viewerId, words);
        return mine(viewerId);
    }

    @Transactional
    public SafetyView update(long viewerId, SafetyPatch patch) {
        UserAccount me = authors.find(viewerId).orElseThrow(CommonExceptions.Unauthenticated::new);
        if (patch != null && patch.defaultFilter() != null) {
            if (!patch.defaultFilter() && age.protectedMinor(me.getUserDateOfBirth())) {
                throw new CommonExceptions.Forbidden("Para menores de 16 anos o filtro de ofensas fica ligado; só o "
                        + "responsável pode alterá-lo (ECA Digital).");
            }
            safety.setDefaultFilter(viewerId, patch.defaultFilter());
        }
        return mine(viewerId);
    }

    private void requireOther(long viewerId, long otherId) {
        if (viewerId == otherId) {
            throw new CommonExceptions.Invalid(List.of("Você não pode bloquear nem silenciar a própria conta."));
        }
        UserAccount other = authors.find(otherId)
                .filter(u -> u.getAccountStatus() != AccountStatus.ANONYMOUS_LGPD_DELETED)
                .orElseThrow(() -> new CommonExceptions.NotFound("Perfil não encontrado."));
        if (other.getId() == null) {
            throw new CommonExceptions.NotFound("Perfil não encontrado.");
        }
    }

    private List<RelatedUserView> related(List<Related> rows) {
        Map<Long, UserAccount> byId = authors.findAll(rows.stream().map(Related::userId).toList());
        return rows.stream().filter(r -> byId.containsKey(r.userId()))
                .map(r -> new RelatedUserView(authors.view(byId.get(r.userId())), r.since())).toList();
    }
}
