package com.friendlyeats.application.establishment;

import com.friendlyeats.domain.model.Establishment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Quem edita a página do lugar (RF6, RF11). Hoje, só a conta dona. Ponto de extensão do RF20 (gestão da página): a
 * equipe com papéis (dono, gerente, social media) entra aqui, sem mexer no serviço nem no controller; o público só
 * vê a marca, e a ação continua assinada pelo estabelecimento.
 */
@Component
public class PlaceEditors {

    public boolean canEdit(Establishment e, long viewerId) {
        return Objects.equals(e.getOwnerId(), viewerId);
    }
}
