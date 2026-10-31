package com.friendlyeats.application.safety;

import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.SafetyRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Regras de leitura do RF19, usadas pelo post (RF14), comentários (RF12) e perfil (RF15):
 * <ul>
 *   <li>bloqueio vale nos dois sentidos e é sigiloso: quem foi bloqueado recebe o mesmo {@code 404} de algo que não
 *       existe;</li>
 *   <li>silenciar esconde só para quem silenciou, sem o outro saber;</li>
 *   <li>avaliações (RF8) não somem por bloqueio: o dono não pode esconder a crítica bloqueando quem avaliou
 *       (RC-SEG-09, riscos);</li>
 *   <li>avisos (notificações, RF21 ⏳) devem consultar {@link #mayReach(Long, long)} antes de sair.</li>
 * </ul>
 */
@Component
public class SafetyGuard {

    private final SafetyRepository safety;
    private final AgePolicy age;

    public SafetyGuard(SafetyRepository safety, AgePolicy age) {
        this.safety = safety;
        this.age = age;
    }

    public boolean blockedEitherWay(Long viewerId, long otherId) {
        return viewerId != null && viewerId != otherId && safety.blockedEitherWay(viewerId, otherId);
    }

    /** Contas que {@code viewerId} não vê: bloqueios nos dois sentidos e silenciados por ele. */
    public Set<Long> hiddenFrom(Long viewerId) {
        return viewerId == null ? Set.of() : safety.hiddenFrom(viewerId);
    }

    /** Uma interação de {@code actorId} pode virar aviso para {@code recipientId}? (para as notificações do RF21) */
    public boolean mayReach(Long actorId, long recipientId) {
        return actorId == null || (!safety.blockedEitherWay(actorId, recipientId) && !safety.mutes(recipientId, actorId));
    }

    /** Palavras que escondem comentários nos posts de {@code owner}: a lista dele e, se ligado, a padrão. */
    public List<String> filterOf(UserAccount owner) {
        List<String> words = new ArrayList<>(safety.words(owner.getId()));
        if (defaultFilterEnabled(owner)) {
            WordFilter.DEFAULT_WORDS.stream().map(WordFilter::normalize).forEach(words::add);
        }
        return words;
    }

    /** Sem escolha gravada, o filtro padrão vem ligado para menor de 16; o menor não consegue desligar. */
    public boolean defaultFilterEnabled(UserAccount user) {
        boolean minor = age.protectedMinor(user.getUserDateOfBirth());
        return minor || safety.defaultFilter(user.getId()).orElse(false);
    }
}
