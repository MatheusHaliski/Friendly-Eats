package com.friendlyeats.application.post;

import com.friendlyeats.application.post.PostDrafts.AlertDraft;
import com.friendlyeats.application.post.PostDrafts.PostDraft;
import com.friendlyeats.application.post.PostDrafts.RecipeDraft;
import com.friendlyeats.domain.model.AgeRating;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.FeedType;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.Allergen;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.friendlyeats.domain.model.UserCategoryEnum.CHEF;
import static com.friendlyeats.domain.model.UserCategoryEnum.COMENSAL;
import static com.friendlyeats.domain.model.UserCategoryEnum.COMUNIDADE;
import static com.friendlyeats.domain.model.UserCategoryEnum.CRIADOR;
import static com.friendlyeats.domain.model.UserCategoryEnum.ESTABELECIMENTO;
import static com.friendlyeats.domain.model.UserCategoryEnum.OFICIAL;
import static com.friendlyeats.domain.model.UserCategoryEnum.VAREJO;
import static org.assertj.core.api.Assertions.assertThat;

/** Regras do post de comida (RF9): quem publica cada tipo, validações, 18+ automático e expiração. */
class PostRulesTest {

    static final Instant NOW = Instant.parse("2026-10-25T15:00:00Z");     // 12h em São Paulo

    @Test
    void eachKindHasItsPublishersAndOrganizationsWaitForTheSeal() {
        CategoryVerification ok = CategoryVerification.VERIFIED;
        CategoryVerification none = CategoryVerification.NOT_REQUIRED;
        for (UserCategoryEnum t : UserCategoryEnum.values()) {
            CategoryVerification seal = t.needsSeal() ? ok : none;
            assertThat(PostKind.FOTO_PRATO.denialFor(t, seal)).as("foto por %s", t).isNull();
            assertThat(PostKind.RECEITA.denialFor(t, seal)).as("receita por %s", t).isNull();
        }
        assertThat(PostKind.PERGUNTA.denialFor(COMENSAL, none)).isNull();
        assertThat(PostKind.PERGUNTA.denialFor(OFICIAL, ok)).contains("Oficial não publica pergunta");

        assertThat(PostKind.PRATO_DO_DIA.denialFor(ESTABELECIMENTO, ok)).isNull();
        assertThat(PostKind.PRATO_DO_DIA.denialFor(VAREJO, ok)).isNotNull();
        assertThat(PostKind.PRATO_DO_DIA.denialFor(CHEF, none)).isNotNull();

        assertThat(PostKind.PROMOCAO.denialFor(ESTABELECIMENTO, ok)).isNull();
        assertThat(PostKind.PROMOCAO.denialFor(VAREJO, ok)).isNull();
        assertThat(PostKind.PROMOCAO.denialFor(CRIADOR, none)).isNull();
        assertThat(PostKind.PROMOCAO.denialFor(COMENSAL, none)).contains("Publicidade");
        assertThat(PostKind.PROMOCAO.denialFor(COMUNIDADE, none)).isNotNull();

        for (UserCategoryEnum host : List.of(ESTABELECIMENTO, VAREJO, COMUNIDADE, CHEF)) {
            assertThat(PostKind.EVENTO.denialFor(host, host.needsSeal() ? ok : none)).isNull();
        }
        assertThat(PostKind.EVENTO.denialFor(COMENSAL, none)).isNotNull();
        assertThat(PostKind.EVENTO.denialFor(CRIADOR, none)).isNotNull();

        assertThat(PostKind.ALERTA_SANITARIO.denialFor(OFICIAL, ok)).isNull();
        assertThat(PostKind.ALERTA_SANITARIO.denialFor(ESTABELECIMENTO, ok)).contains("Oficial verificada");
        // selo em conferência: nenhuma organização publica nada, nem foto
        for (CategoryVerification seal : List.of(CategoryVerification.PENDING, CategoryVerification.REJECTED)) {
            assertThat(PostKind.ALERTA_SANITARIO.denialFor(OFICIAL, seal)).contains("em verificação");
            assertThat(PostKind.FOTO_PRATO.denialFor(ESTABELECIMENTO, seal)).contains("em verificação");
        }
        assertThat(PostKind.PRATO_DO_DIA.needsOwnedPlace(ESTABELECIMENTO)).isTrue();
        assertThat(PostKind.PROMOCAO.needsOwnedPlace(VAREJO)).isTrue();
        assertThat(PostKind.PROMOCAO.needsOwnedPlace(CRIADOR)).isFalse();
        assertThat(Arrays.stream(PostKind.values()).filter(k -> k.feed() == FeedType.ALERT))
                .containsExactly(PostKind.ALERTA_SANITARIO);
    }

    @Test
    void dishPhotoNeedsThePhotoWithAltTextAndFieldsOfOtherKindsAreRefused() {
        List<String> errors = new ArrayList<>();
        PostRules.validate(PostKind.FOTO_PRATO, draft().title("Coxinha").discountPercent(10).recipe(
                new RecipeDraft(List.of("x"), List.of("y"), null, null)).build(), ctx(COMENSAL, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("Foto de prato precisa da foto"))
                .anyMatch(e -> e.contains("\"discountPercent\" não se aplica a Foto de prato"))
                .anyMatch(e -> e.contains("\"recipe\" não se aplica"));

        errors.clear();
        PostRules.validate(PostKind.FOTO_PRATO, draft().title("Coxinha").build(), ctx(COMENSAL, true), errors);
        assertThat(errors).anyMatch(e -> e.contains("altText"));

        errors.clear();
        PostRules.Content ok = PostRules.validate(PostKind.FOTO_PRATO, draft().title("  Coxinha  ")
                .alt("Coxinha cortada ao meio").allergens(Map.of("TRIGO", "CONTEM", "LEITE", "PODE_CONTER"))
                .warnings(List.of("GLUTEN")).dietary(List.of("VEGETARIANO")).build(), ctx(COMENSAL, true), errors);
        assertThat(errors).isEmpty();
        assertThat(ok.title()).isEqualTo("Coxinha");
        assertThat(ok.allergens()).containsEntry(Allergen.TRIGO, Allergen.Level.CONTEM)
                .containsEntry(Allergen.LEITE, Allergen.Level.PODE_CONTER);
        assertThat(ok.warnings()).containsExactly(Allergen.GLUTEN);
        assertThat(ok.expiresAt()).isNull();
    }

    @Test
    void allergensFollowTheCurrentAnvisaListAndWarningsHaveTheirOwnField() {
        assertThat(Arrays.stream(Allergen.values())
                .filter(a -> a.getKind() == Allergen.Kind.ALERGENICO && a.getStatus() == Allergen.Status.VIGENTE))
                .hasSize(21);
        assertThat(Arrays.stream(Allergen.values()).filter(Allergen::isWarning))
                .containsExactly(Allergen.LACTOSE, Allergen.GLUTEN);

        List<String> errors = new ArrayList<>();
        PostRules.validate(PostKind.RECEITA, draft().title("Tahine").recipe(recipe())
                .allergens(Map.of("GERGELIM", "CONTEM", "LACTOSE", "CONTEM", "OVOS", "TALVEZ", "XYZ", "CONTEM"))
                .warnings(List.of("LEITE")).build(), ctx(COMENSAL, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("Gergelim ainda não está na lista vigente"))
                .anyMatch(e -> e.contains("é advertência, não alergênico"))
                .anyMatch(e -> e.contains("use CONTEM ou PODE_CONTER"))
                .anyMatch(e -> e.contains("Alergênico desconhecido"))
                .anyMatch(e -> e.contains("\"warnings\" aceita só"));
    }

    @Test
    void alcoholicFormatOrBarMakesThePostAdultsOnlyButTheAlertIsAlwaysForEveryone() {
        assertThat(PostRules.ageRating(PostKind.FOTO_PRATO, AgeRating.LIVRE, FoodFormat.CERVEJA, false))
                .isEqualTo(AgeRating.AGE_18);
        assertThat(PostRules.ageRating(PostKind.EVENTO, null, FoodFormat.PETISCOS_BOTECO, true))
                .isEqualTo(AgeRating.AGE_18);
        assertThat(PostRules.ageRating(PostKind.RECEITA, null, FoodFormat.MASSAS, false)).isEqualTo(AgeRating.LIVRE);
        assertThat(PostRules.ageRating(PostKind.ALERTA_SANITARIO, null, FoodFormat.COQUETEIS_DESTILADOS, true))
                .isEqualTo(AgeRating.LIVRE);

        List<String> errors = new ArrayList<>();
        PostRules.validate(PostKind.RECEITA, draft().title("Caipirinha").recipe(recipe()).format("COQUETEIS_DESTILADOS")
                .build(), new PostRules.Context(COMENSAL, false, false, false, NOW, null, null), errors);
        assertThat(errors).anyMatch(e -> e.contains("Menores de 18 anos não publicam"));
    }

    @Test
    void promotionNeedsPeriodAndFromToOrDiscountAndTheCreatorNeedsTheAdvertisingChip() {
        List<String> errors = new ArrayList<>();
        PostRules.validate(PostKind.PROMOCAO, draft().title("Combo").build(), ctx(ESTABELECIMENTO, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("vigência da promoção"))
                .anyMatch(e -> e.contains("um dos dois"));

        errors.clear();
        PostRules.validate(PostKind.PROMOCAO, draft().title("Combo").period(NOW.plusSeconds(3600), NOW)
                .fromTo(3000, 4000).discountPercent(95).build(), ctx(CRIADOR, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("depois do início"))
                .anyMatch(e -> e.contains("\"de\" precisa ser maior"))
                .anyMatch(e -> e.contains("Desconto entre 1% e 90%"))
                .anyMatch(e -> e.contains("chip Publicidade"));

        errors.clear();
        PostRules.Content c = PostRules.validate(PostKind.PROMOCAO, draft().title("Combo").advertising(true)
                .period(NOW, NOW.plus(Duration.ofDays(5))).discountPercent(20).build(), ctx(CRIADOR, false), errors);
        assertThat(errors).isEmpty();
        assertThat(c.expiresAt()).isEqualTo(NOW.plus(Duration.ofDays(5)));

        errors.clear();
        PostRules.validate(PostKind.PROMOCAO, draft().title("Velha").period(NOW.minusSeconds(7200), NOW.minusSeconds(60))
                .discountPercent(10).build(), ctx(ESTABELECIMENTO, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("já passou"));
    }

    @Test
    void eventIsPaidOrFreeTheDailyDishHasAPriceAndTheAlertNeedsAnOfficialHttpsSource() {
        List<String> errors = new ArrayList<>();
        PostRules.validate(PostKind.EVENTO, draft().title("Festival").period(NOW, NOW.plusSeconds(7200)).price(1000)
                .free(true).build(), ctx(COMUNIDADE, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("ou marque \"free\""));

        errors.clear();
        PostRules.Content free = PostRules.validate(PostKind.EVENTO, draft().title("Festival")
                .period(NOW, NOW.plusSeconds(7200)).free(true).build(), ctx(COMUNIDADE, false), errors);
        assertThat(errors).isEmpty();
        assertThat(free.free()).isTrue();

        errors.clear();
        PostRules.validate(PostKind.PRATO_DO_DIA, draft().title("Feijoada").build(), ctx(ESTABELECIMENTO, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("CDC art. 31"));

        errors.clear();
        PostRules.validate(PostKind.ALERTA_SANITARIO, draft().title("Recall").ageRating("AGE_18").advertising(true)
                .alert(new AlertDraft("Queijo", "Marca", List.of(), "Listeria", "http://exemplo.gov.br/x")).build(),
                ctx(OFICIAL, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("sempre Livre")).anyMatch(e -> e.contains("não é publicidade"))
                .anyMatch(e -> e.contains("lotes")).anyMatch(e -> e.contains("https"));
        assertThat(PostRules.httpsUrl("https://www.gov.br/anvisa/pt-br/alertas")).isTrue();
        assertThat(PostRules.httpsUrl("https://usuario@site.example/x")).isFalse();
        assertThat(PostRules.httpsUrl("javascript:alert(1)")).isFalse();
    }

    @Test
    void dailyDishExpiresAt2359InSaoPauloOnThePublicationDay() {
        // 01h30 UTC de 26/10 ainda é 25/10 em São Paulo (UTC-3)
        assertThat(PostRules.expiresAt(PostKind.PRATO_DO_DIA, Instant.parse("2026-10-26T01:30:00Z"), null))
                .isEqualTo(Instant.parse("2026-10-26T02:59:59Z"));
        assertThat(PostRules.expiresAt(PostKind.PRATO_DO_DIA, NOW, null)).isEqualTo(Instant.parse("2026-10-26T02:59:59Z"));
        assertThat(PostRules.expiresAt(PostKind.EVENTO, NOW, NOW.plusSeconds(60))).isEqualTo(NOW.plusSeconds(60));
        assertThat(PostRules.expiresAt(PostKind.RECEITA, NOW, null)).isNull();
        assertThat(PostKind.PRATO_DO_DIA.expires()).isTrue();
        assertThat(PostKind.FOTO_PRATO.expires()).isFalse();
    }

    @Test
    void recipeNeedsIngredientsAndSteps() {
        List<String> errors = new ArrayList<>();
        PostRules.validate(PostKind.RECEITA, draft().title("Bolo").recipe(new RecipeDraft(List.of(" "), null, 0, 5000))
                .build(), ctx(COMENSAL, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("ingrediente")).anyMatch(e -> e.contains("passo"))
                .anyMatch(e -> e.contains("Rendimento")).anyMatch(e -> e.contains("48 horas"));
        errors.clear();
        PostRules.validate(PostKind.RECEITA, draft().title("Bolo").build(), ctx(COMENSAL, false), errors);
        assertThat(errors).anyMatch(e -> e.contains("ingredientes e o modo de preparo"));
    }

    // ------------------------------------------------------------ apoio

    private static PostRules.Context ctx(UserCategoryEnum type, boolean photo) {
        return new PostRules.Context(type, true, false, photo, NOW, null, null);
    }

    private static RecipeDraft recipe() {
        return new RecipeDraft(List.of("1 xícara de gergelim"), List.of("Bata tudo."), 4, 10);
    }

    static Builder draft() {
        return new Builder();
    }

    static final class Builder {
        String title, alt, format, ageRating;
        Map<String, String> allergens;
        List<String> warnings, dietary;
        Boolean advertising, free;
        Instant starts, ends;
        Integer price, from, to, discount;
        RecipeDraft recipe;
        AlertDraft alert;

        Builder title(String v) { title = v; return this; }
        Builder alt(String v) { alt = v; return this; }
        Builder format(String v) { format = v; return this; }
        Builder ageRating(String v) { ageRating = v; return this; }
        Builder allergens(Map<String, String> v) { allergens = v; return this; }
        Builder warnings(List<String> v) { warnings = v; return this; }
        Builder dietary(List<String> v) { dietary = v; return this; }
        Builder advertising(boolean v) { advertising = v; return this; }
        Builder free(boolean v) { free = v; return this; }
        Builder period(Instant s, Instant e) { starts = s; ends = e; return this; }
        Builder price(int v) { price = v; return this; }
        Builder fromTo(int f, int t) { from = f; to = t; return this; }
        Builder discountPercent(int v) { discount = v; return this; }
        Builder recipe(RecipeDraft v) { recipe = v; return this; }
        Builder alert(AlertDraft v) { alert = v; return this; }

        PostDraft build() {
            return new PostDraft(null, title, null, null, alt, null, null, format, dietary, allergens, warnings,
                    ageRating, advertising, starts, ends, price, from, to, discount, free, recipe, alert, null, null);
        }
    }
}
