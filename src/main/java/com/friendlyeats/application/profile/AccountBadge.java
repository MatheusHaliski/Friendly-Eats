package com.friendlyeats.application.profile;

import com.friendlyeats.domain.model.UserCategoryEnum;

/**
 * "Bolinha" do tipo de conta (RF15; docs/DESIGN_SYSTEM.md, seção 3): cor nos dois temas, glifo geométrico, ícone
 * Lucide e rótulo, porque a cor nunca é o único sinal. A cor escura vem do {@link UserCategoryEnum}; a clara e os
 * glifos, da taxonomia (seção 2.1).
 */
public record AccountBadge(String code, String label, String color, String lightColor, String glyph,
                           String glyphName, String lucideIcon) {

    public static AccountBadge of(UserCategoryEnum type) {
        return switch (type) {
            case COMENSAL -> badge(type, "#05558F", "◇", "losango", "user-round");
            case CRIADOR -> badge(type, "#876A06", "✦", "estrela", "camera");
            case CHEF -> badge(type, "#7D5BBA", "⬠", "pentágono", "chef-hat");
            case ESTABELECIMENTO -> badge(type, "#7F4D06", "▭", "retângulo", "store");
            case VAREJO -> badge(type, "#96075A", "△", "triângulo", "shopping-basket");
            case COMUNIDADE -> badge(type, "#0B8170", "⬢", "hexágono", "users");
            case OFICIAL -> badge(type, "#0C0A07", "◎", "círculo duplo", "landmark");
        };
    }

    private static AccountBadge badge(UserCategoryEnum type, String light, String glyph, String glyphName, String icon) {
        return new AccountBadge(type.name(), type.getLabel(), type.getHexColor(), light, glyph, glyphName, icon);
    }
}
