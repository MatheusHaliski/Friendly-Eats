package com.friendlyeats.application.background;

import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.MediaType;
import com.friendlyeats.domain.model.UserCategoryEnum;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leitura da moldura (RF16): transforma a configuração salva no post no que o card precisa para desenhar (arte
 * escolhida, cartela do dia no modo automático e tinta do container já calculada). Também sugere a "direção
 * recomendada" do estúdio pelo tipo de conta.
 */
@Service
public class BackgroundService {

    public static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");

    public record BackgroundView(Map<String, Object> config, String artKind, String artId, List<String> palette,
                                 String css, ContainerView container) {
    }

    public record ContainerView(String color, String ink, String contrast) {
    }

    public record Recommendation(String auraId, String materialId, String seasonId, String reason) {
    }

    /** Padrão + material que combinam com cada tipo de conta; o primeiro é o mais indicado. */
    private static final Map<UserCategoryEnum, List<String[]>> BY_CATEGORY = Map.of(
            UserCategoryEnum.COMENSAL, List.of(new String[]{"toalha-xadrez", "linho"}, new String[]{"feira", "papel-manteiga"}),
            UserCategoryEnum.CRIADOR, List.of(new String[]{"neon", "vidro"}, new String[]{"azulejo", "papel-manteiga"}),
            UserCategoryEnum.CHEF, List.of(new String[]{"forno", "inox"}, new String[]{"horta", "linho"}),
            UserCategoryEnum.ESTABELECIMENTO, List.of(new String[]{"azulejo", "papel-manteiga"}, new String[]{"forno", "inox"}),
            UserCategoryEnum.VAREJO, List.of(new String[]{"feira", "papel-manteiga"}, new String[]{"favo", "vidro"}),
            UserCategoryEnum.COMUNIDADE, List.of(new String[]{"feira", "linho"}, new String[]{"toalha-xadrez", "papel-manteiga"}),
            UserCategoryEnum.OFICIAL, List.<String[]>of(new String[]{"horta", "papel-manteiga"}));

    private final BackgroundCatalog catalog;
    private final Clock clock;

    public BackgroundService(BackgroundCatalog catalog, Clock clock) {
        this.catalog = catalog;
        this.clock = clock;
    }

    /** Data de referência das cartelas: sempre o dia em São Paulo. */
    public LocalDate today() {
        return LocalDate.now(clock.withZone(SAO_PAULO));
    }

    public BackgroundCatalog.CatalogView catalog() {
        return catalog.view(today());
    }

    /** {@code null} quando o post usa a moldura padrão. */
    public BackgroundView resolve(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        String layoutId = (String) config.get("layoutId");
        String kind;
        String id = null;
        List<String> palette = List.of();
        String css = null;
        Map<?, ?> seasonal = (Map<?, ?>) config.get("seasonal");
        Map<?, ?> aura = (Map<?, ?>) config.get("aura");
        BackgroundCatalog.Layout layout = catalog.layoutsById().get(layoutId);
        if (layout != null && layout.ownArt()) {
            kind = "LAYOUT";
            id = layoutId;
        } else if (aura != null) {
            kind = "AURA";
            id = (String) aura.get("presetId");
            BackgroundCatalog.Aura a = BackgroundCatalog.AURAS.stream().filter(x -> x.id().equals(aura.get("presetId"))).findFirst().orElseThrow();
            palette = a.palette();
            css = a.css();
        } else if (seasonal != null && (Boolean.TRUE.equals(seasonal.get("auto")) || seasonal.get("presetId") != null)) {
            Optional<SeasonalCalendar.Season> season = Boolean.TRUE.equals(seasonal.get("auto"))
                    ? SeasonalCalendar.activeOn(today())
                    : SeasonalCalendar.byId((String) seasonal.get("presetId"));
            kind = "SEASONAL";
            id = season.map(SeasonalCalendar.Season::id).orElse(null);
            palette = season.map(SeasonalCalendar.Season::colors).orElse(List.of());
            css = palette.isEmpty() ? null : seasonGradient(palette);
        } else if (config.get("gradientId") instanceof String gradientId) {
            BackgroundCatalog.Item g = BackgroundCatalog.GRADIENTS.stream().filter(x -> x.id().equals(gradientId)).findFirst().orElseThrow();
            kind = "GRADIENT";
            id = gradientId;
            palette = g.palette();
            css = g.css();
        } else if (config.get("color") instanceof String color) {
            kind = "COLOR";
            id = color;
            palette = List.of(color);
            css = color;
        } else {
            kind = "NONE";
        }
        return new BackgroundView(config, kind, id, palette, css, container(config));
    }

    /** Direção recomendada: padrões que combinam com o tipo de conta do autor, mais a cartela do dia. */
    public List<Recommendation> recommend(UserCategoryEnum category, FeedType feed, MediaType media) {
        if (feed == FeedType.ALERT) {
            return List.of();
        }
        List<Recommendation> out = new ArrayList<>();
        for (String[] pair : BY_CATEGORY.get(category)) {
            // foto ou vídeo do prato já chama atenção: sugere o padrão sem material por cima
            String material = media == MediaType.IMAGE || media == MediaType.VIDEO ? null : pair[1];
            out.add(new Recommendation(pair[0], material, null,
                    "Combina com " + category.getLabel() + (material == null ? " e deixa o prato em destaque" : " e post de texto")));
        }
        SeasonalCalendar.activeOn(today()).ifPresent(s ->
                out.add(new Recommendation(null, null, s.id(), "Cartela da época: " + s.name())));
        return out;
    }

    private ContainerView container(Map<String, Object> config) {
        Map<?, ?> c = (Map<?, ?>) config.get("container");
        BackgroundCatalog.Skin skin = catalog.skinsById().get((String) config.get("skinId"));
        String bg = c != null && c.get("color") instanceof String color ? color : skin.containerColor();
        String ink = c != null && c.get("ink") instanceof String i && !"AUTO".equals(i) ? i : Contrast.bestInk(bg);
        return new ContainerView(bg, ink, Contrast.format(Contrast.ratio(ink, bg)));
    }

    private static String seasonGradient(List<String> colors) {
        StringBuilder css = new StringBuilder("linear-gradient(135deg");
        for (int i = 0; i < colors.size(); i++) {
            css.append(',').append(colors.get(i)).append(' ').append(i * 100 / (colors.size() - 1)).append('%');
        }
        return css.append(')').toString();
    }
}
