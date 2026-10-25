package com.friendlyeats.application.background;

import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.MediaType;
import com.friendlyeats.domain.model.UserCategoryEnum;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** RF16: cartelas da época no Hemisfério Sul, regras da moldura e contraste, sem Spring. */
class BackgroundRulesTest {

    final BackgroundCatalog catalog = new BackgroundCatalog();
    final BackgroundValidator validator = new BackgroundValidator(catalog);
    final BackgroundValidator.Context post = new BackgroundValidator.Context(FeedType.POST, false);

    // ------------------------------------------------------------ cartela da época

    @Test
    void carnavalAndEasterComeFromTheEasterDate() {
        assertThat(SeasonalCalendar.easter(2026)).isEqualTo(LocalDate.of(2026, 4, 5));
        assertThat(SeasonalCalendar.easter(2027)).isEqualTo(LocalDate.of(2027, 3, 28));
        assertThat(SeasonalCalendar.easter(2028)).isEqualTo(LocalDate.of(2028, 4, 16));
        assertThat(SeasonalCalendar.easter(2028).minusDays(47)).isEqualTo(LocalDate.of(2028, 2, 29));   // ano bissexto

        assertThat(active(2026, 2, 13)).isEqualTo("carnaval");    // sexta
        assertThat(active(2026, 2, 18)).isEqualTo("carnaval");    // Quarta de Cinzas
        assertThat(active(2026, 2, 19)).isEqualTo("verao");
        assertThat(active(2026, 3, 28)).isEqualTo("outono");
        assertThat(active(2026, 3, 29)).isEqualTo("pascoa");      // Domingo de Ramos
        assertThat(active(2026, 4, 5)).isEqualTo("pascoa");       // Domingo de Páscoa
        assertThat(active(2026, 4, 6)).isEqualTo("outono");
        assertThat(active(2027, 3, 21)).isEqualTo("pascoa");      // Páscoa cedo: a Semana Santa vence o outono
    }

    @Test
    void higherPriorityWinsAndWindowsAreHalfOpen() {
        assertThat(active(2026, 6, 12)).isEqualTo("namorados");       // data pontual (1) vence as juninas (3)
        assertThat(active(2026, 6, 13)).isEqualTo("junina");
        assertThat(active(2026, 7, 10)).isEqualTo("dia-da-pizza");
        assertThat(active(2026, 7, 11)).isEqualTo("junina");          // festas julinas
        assertThat(active(2026, 8, 1)).isEqualTo("inverno");
        assertThat(active(2026, 10, 25)).isEqualTo("primavera");
        assertThat(active(2026, 12, 1)).isEqualTo("natal");           // festa (3) vence primavera (4)
        assertThat(active(2027, 1, 6)).isEqualTo("natal");            // janela que vira o ano
        assertThat(active(2027, 1, 7)).isEqualTo("verao");
        assertThat(active(2026, 3, 20)).isEqualTo("outono");          // [início, fim): 20/03 já é outono
    }

    @Test
    void blackFridayIsNeverAutomatic() {
        assertThat(SeasonalCalendar.isBlackFriday(LocalDate.of(2026, 11, 27))).isTrue();
        assertThat(SeasonalCalendar.isBlackFriday(LocalDate.of(2027, 11, 26))).isTrue();
        assertThat(active(2026, 11, 27)).isEqualTo("primavera");
        assertThat(SeasonalCalendar.byId("black-friday")).get().extracting(SeasonalCalendar.Season::auto).isEqualTo(false);
    }

    // ------------------------------------------------------------ validação da moldura

    @Test
    void canonicalFormFillsDefaultsAndKeepsOnlyIds() {
        Map<String, Object> out = validator.validate(Map.of("aura", Map.of("presetId", "toalha-xadrez")), post);
        assertThat(out).containsEntry("v", 1).containsEntry("layoutId", "CLASSICO").containsEntry("skinId", "PADRAO")
                .containsEntry("animationId", "NONE");
        assertThat(out.get("aura")).isEqualTo(Map.of("presetId", "toalha-xadrez", "variantId", "ORIGINAL", "format", "IMAGEM_UNICA"));
        assertThat(out.keySet()).containsExactly("v", "color", "gradientId", "seasonal", "animationId", "aura",
                "materialId", "customArt", "layoutId", "skinId", "container");
        assertThat(validator.validate(Map.of("color", "#e88f12"), post)).containsEntry("color", "#E88F12");
        assertThat(validator.validate(null, post)).isNull();
    }

    @Test
    void rejectsUnknownKeysCssUrlsAndIdsOutsideTheCatalog() {
        assertThat(errors(Map.of("gradient", "linear-gradient(red,blue)"))).anyMatch(e -> e.contains("\"background.gradient\""));
        assertThat(errors(Map.of("aura", Map.of("presetId", "feira", "url", "https://evil.example/x.gif"))))
                .anyMatch(e -> e.contains("aura.url"));
        assertThat(errors(Map.of("gradientId", "arco-iris"))).anyMatch(e -> e.contains("Gradiente desconhecido"));
        assertThat(errors(Map.of("color", "#48A8FF"))).anyMatch(e -> e.contains("fora do catálogo"));   // cor de tipo de conta
        assertThat(errors(Map.of("v", 2))).anyMatch(e -> e.contains("v = 1"));
    }

    @Test
    void oneArtSourceAndMenuLayoutDoesNotMixWithOtherArt() {
        assertThat(errors(Map.of("gradientId", "acai", "aura", Map.of("presetId", "mare"))))
                .anyMatch(e -> e.contains("única arte"));
        assertThat(errors(Map.of("layoutId", "CARDAPIO", "materialId", "vidro"))).anyMatch(e -> e.contains("Cardápio"));
        assertThat(errors(Map.of("seasonal", Map.of("presetId", "natal", "auto", true)))).anyMatch(e -> e.contains("não os dois"));
        assertThat(validator.validate(Map.of("layoutId", "CARDAPIO", "skinId", "MONO"), post)).containsEntry("layoutId", "CARDAPIO");
    }

    @Test
    void alertsHaveNoFrameAndMinorsCannotUseCustomArt() {
        assertThatThrownBy(() -> validator.validate(Map.of("color", "#2A2119"),
                new BackgroundValidator.Context(FeedType.ALERT, false)))
                .isInstanceOf(CommonExceptions.Invalid.class).hasMessageContaining("Alertas sanitários");
        assertThatThrownBy(() -> validator.validate(Map.of("customArt", Map.of("assetId", "a1", "origin", "IA")),
                new BackgroundValidator.Context(FeedType.POST, true)))
                .isInstanceOf(CommonExceptions.Forbidden.class).hasMessageContaining("ECA Digital");
        assertThat(errors(Map.of("customArt", Map.of("assetId", "a1", "origin", "UPLOAD")))).anyMatch(e -> e.contains("RF13"));
    }

    @Test
    void containerInkNeedsAaContrastAndSuggestsTheBetterInk() {
        Map<String, Object> bad = Map.of("container", Map.of("color", "#F5EDE3", "ink", "#FF9F5A"));
        assertThat(errors(bad)).anyMatch(e -> e.contains("mínimo 4,5:1") && e.contains("#17120D") && e.endsWith(")."));
        assertThat(validator.validate(Map.of("container", Map.of("color", "#E88F12", "ink", "AUTO")), post))
                .extracting(m -> ((Map<?, ?>) m.get("container")).get("ink")).isEqualTo("AUTO");
        assertThat(Contrast.bestInk("#E88F12")).isEqualTo(Contrast.DARK_INK);
        assertThat(Contrast.ratio(Contrast.DARK_INK, "#E88F12")).isGreaterThan(Contrast.AA_TEXT);
        // toda cor de container do catálogo tem uma tinta automática que passa no AA
        assertThat(BackgroundCatalog.CONTAINER_COLORS).allSatisfy(c ->
                assertThat(Contrast.ratio(Contrast.bestInk(c.id()), c.id())).isGreaterThanOrEqualTo(Contrast.AA_TEXT));
        assertThat(BackgroundCatalog.SKINS).allSatisfy(s ->
                assertThat(Contrast.ratio(Contrast.bestInk(s.containerColor()), s.containerColor())).isGreaterThanOrEqualTo(Contrast.AA_TEXT));
    }

    // ------------------------------------------------------------ leitura e recomendação

    @Test
    void resolveTurnsTheSavedConfigIntoWhatTheCardDraws() {
        BackgroundService service = new BackgroundService(catalog,
                Clock.fixed(Instant.parse("2026-07-10T15:00:00Z"), ZoneOffset.UTC));
        BackgroundService.BackgroundView auto = service.resolve(validator.validate(Map.of("seasonal", Map.of("auto", true)), post));
        assertThat(auto.artKind()).isEqualTo("SEASONAL");
        assertThat(auto.artId()).isEqualTo("dia-da-pizza");
        assertThat(auto.css()).startsWith("linear-gradient(135deg,#C1121F 0%");
        assertThat(auto.container().ink()).isEqualTo(Contrast.LIGHT_INK);

        assertThat(service.resolve(validator.validate(Map.of("layoutId", "CARDAPIO"), post)).artKind()).isEqualTo("LAYOUT");
        assertThat(service.resolve(validator.validate(Map.of(), post)).artKind()).isEqualTo("NONE");
        assertThat(service.resolve(null)).isNull();

        List<BackgroundService.Recommendation> forChef = service.recommend(UserCategoryEnum.CHEF, FeedType.POST, MediaType.IMAGE);
        assertThat(forChef.getFirst().auraId()).isEqualTo("forno");
        assertThat(forChef.getFirst().materialId()).isNull();                  // foto do prato em destaque
        assertThat(forChef.getLast().seasonId()).isEqualTo("dia-da-pizza");
        assertThat(service.recommend(UserCategoryEnum.OFICIAL, FeedType.ALERT, MediaType.TEXT)).isEmpty();
        assertThat(UserCategoryEnum.values()).allSatisfy(c ->
                assertThat(service.recommend(c, FeedType.POST, MediaType.TEXT)).isNotEmpty());
    }

    @Test
    void catalogVersionIsStable() {
        assertThat(new BackgroundCatalog().version()).isEqualTo(catalog.version()).hasSize(16);
        assertThat(catalog.view(LocalDate.of(2026, 6, 20)).activeSeasonId()).isEqualTo("junina");
    }

    private static String active(int y, int m, int d) {
        return SeasonalCalendar.activeOn(LocalDate.of(y, m, d)).orElseThrow().id();
    }

    private List<String> errors(Map<String, Object> raw) {
        try {
            validator.validate(new HashMap<>(raw), post);
        } catch (CommonExceptions.Invalid e) {
            return e.errors();
        }
        throw new AssertionError("esperava 422 para " + raw);
    }
}
