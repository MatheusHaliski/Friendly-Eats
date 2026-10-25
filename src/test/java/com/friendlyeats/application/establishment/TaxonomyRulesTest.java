package com.friendlyeats.application.establishment;

import com.friendlyeats.application.establishment.EstablishmentDrafts.ClassificationDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.LocationDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.PriceDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.ReviewDraft;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.AddressVisibility;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.CuisineChip;
import com.friendlyeats.domain.taxonomy.CuisineMacro;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.EstablishmentGroup;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.FoodFamily;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.PriceLevel;
import com.friendlyeats.domain.taxonomy.ReviewDimension;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Taxonomia (docs/TAXONOMIA_FRIENDLY_EATS.md) e as regras de RF6, RF7 e RF8 sem banco. */
class TaxonomyRulesTest {

    static final Instant NOW = Instant.parse("2026-10-25T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void vocabulariesHaveTheResearchedSizeAndEveryCuisineDescendsFromItsMacroGeneric() {
        assertThat(Cuisine.values()).hasSize(56);
        assertThat(CuisineMacro.values()).hasSize(7);
        assertThat(FoodFormat.values()).hasSize(33);
        assertThat(FoodFamily.values()).hasSize(8);
        assertThat(EstablishmentType.values()).hasSize(48);
        assertThat(EstablishmentGroup.values()).hasSize(7);
        for (CuisineMacro macro : CuisineMacro.values()) {
            List<Cuisine> generic = Arrays.stream(Cuisine.values())
                    .filter(c -> c.getMacro() == macro && c.isMacroGeneric()).toList();
            assertThat(generic).as("genérica de %s", macro).hasSize(1);
            Arrays.stream(Cuisine.values()).filter(c -> c.getMacro() == macro && c != generic.getFirst())
                    .forEach(c -> assertThat(generic.getFirst().isAncestorOf(c)).as(c.name()).isTrue());
        }
        assertThat(Cuisine.NORDESTINA.isAncestorOf(Cuisine.BAIANA)).isTrue();
        assertThat(Cuisine.BAIANA.isAncestorOf(Cuisine.NORDESTINA)).isFalse();
        assertThat(Arrays.stream(EstablishmentType.values()).filter(EstablishmentType::isAlcoholFocused).map(Enum::name))
                .containsExactlyInAnyOrder("BAR", "BOTECO", "GASTROBAR", "BAR_COQUETELARIA", "BAR_VINHOS", "CERVEJARIA",
                        "VINICOLA_DESTILARIA", "ADEGA");
        // um ícone, um significado: chef-hat é da conta Chef, nunca de culinária
        assertThat(Arrays.stream(CuisineChip.values()).map(CuisineChip::getLucideIcon)).doesNotHaveDuplicates()
                .doesNotContain("chef-hat");
    }

    @Test
    void typesFollowTheAccountKindAndPolosAreNeverSecondary() {
        List<String> errors = new ArrayList<>();
        EstablishmentRules.Types ok = EstablishmentRules.types(UserCategoryEnum.ESTABELECIMENTO, null, "PADARIA",
                List.of("RESTAURANTE_QUILO", "LANCHONETE"), errors);
        assertThat(errors).isEmpty();
        assertThat(ok.primary().getGroup()).isEqualTo(EstablishmentGroup.CAFE_DOCE);

        assertThat(typeErrors(UserCategoryEnum.VAREJO, null, "BISTRO", List.of())).anyMatch(e -> e.contains("grupo Varejo"));
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, null, "ADEGA", List.of())).anyMatch(e -> e.contains("Varejo & Marca"));
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, null, "FOOD_TRUCK", List.of("FOOD_PARK")))
                .anyMatch(e -> e.contains("só entra como tipo principal"));
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, null, "MERCADO_MUNICIPAL", List.of("FEIRA_LIVRE"))).isEmpty();
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, null, "BAR",
                List.of("BOTECO", "GASTROBAR", "BAR_VINHOS", "CERVEJARIA"))).anyMatch(e -> e.contains("No máximo 3"));
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, null, "TAVERNA", List.of())).anyMatch(e -> e.contains("TAVERNA"));
        // negócio com CPF fica no tipo do seu formato informal
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, "FOOD_TRUCK", "FOOD_TRUCK", List.of())).isEmpty();
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, "FOOD_TRUCK", "RESTAURANTE_A_LA_CARTE", List.of()))
                .anyMatch(e -> e.contains("CPF"));
        assertThat(typeErrors(UserCategoryEnum.ESTABELECIMENTO, "COZINHA_CASEIRA", "MARMITARIA", List.of("COZINHA_ARTESANAL"))).isEmpty();
    }

    @Test
    void classificationRejectsAncestorsAndChecksTheApproach() {
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, "BAIANA", List.of("NORDESTINA"), null, "TRADICIONAL"))
                .containsExactly("Baiana já inclui Nordestina: deixe só Baiana.");
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, "BRASILEIRA", List.of("MINEIRA"), null, null))
                .anyMatch(e -> e.startsWith("Mineira já inclui Brasileira"));
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, "BAIANA", List.of("PARAENSE"), null, null)).isEmpty();
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, null, List.of(), null, null))
                .anyMatch(e -> e.contains("ao menos a culinária ou o tipo de comida"));
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, null, List.of(), null, "VARIADA")).isEmpty();
        assertThat(classErrors(EstablishmentGroup.VAREJO, null, List.of(), null, null)).isEmpty();
        assertThat(classErrors(EstablishmentGroup.RAPIDO, null, List.of(), "PIZZA", null)).isEmpty();
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, null, List.of(), "MASSAS", "CONTEMPORANEA"))
                .anyMatch(e -> e.contains("contemporânea precisa da culinária"));
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, "JAPONESA", List.of(), null, "FUSION"))
                .anyMatch(e -> e.startsWith("Fusion"));
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, "ASIATICA", List.of(), null, "FUSION")).isEmpty();
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, "NIKKEI", List.of("JAPONESA"), "SUSHI", "FUSION")).isEmpty();
        assertThat(classErrors(EstablishmentGroup.RESTAURANTE, "ITALIANA", List.of("FRANCESA", "ESPANHOLA", "GREGA"), null, null))
                .anyMatch(e -> e.contains("No máximo 2"));

        List<String> errors = new ArrayList<>();
        EstablishmentRules.Classification vegan = EstablishmentRules.classification(EstablishmentGroup.RESTAURANTE,
                new ClassificationDraft("MINEIRA", null, null, null, null,
                        Map.of("VEGANO", "EXCLUSIVO", "SEM_GLUTEN", "OFERECE")), errors);
        assertThat(errors).isEmpty();
        assertThat(CuisineChip.of(vegan.primaryCuisine(), vegan.primaryFormat(), vegan.dietaryOffers()))
                .isEqualTo(CuisineChip.VEGETAL);
        assertThat(CuisineChip.of(Cuisine.PERUANA, null, Map.of())).isEqualTo(CuisineChip.AMERICAS);
        assertThat(CuisineChip.of(null, FoodFormat.PIZZA, Map.of())).isEqualTo(CuisineChip.LANCHES_PIZZA);
        assertThat(CuisineChip.of(null, null, Map.of(DietaryStyle.VEGANO, DietaryStyle.Level.OFERECE))).isNull();
        EstablishmentRules.classification(EstablishmentGroup.RESTAURANTE,
                new ClassificationDraft("MINEIRA", null, null, null, null, Map.of("VEGANO", "SEMPRE")), errors);
        assertThat(errors).anyMatch(e -> e.contains("OFERECE ou EXCLUSIVO"));
    }

    @Test
    void locationUsesTheIbgeCodeKeepsPinsInBrazilAndHidesTheHomeKitchenStreet() {
        List<String> errors = new ArrayList<>();
        EstablishmentRules.Location sp = EstablishmentRules.location(new LocationDraft("sp", "3550308", "São Paulo",
                "Pinheiros", "Rua dos Pinheiros", "100", null, "05422-000", -23.5666, -46.6890, null), false, errors);
        assertThat(errors).isEmpty();
        assertThat(sp.cep()).isEqualTo("05422000");
        assertThat(sp.visibility()).isEqualTo(AddressVisibility.ADDR_PUBLIC_FULL);
        assertThat(sp.geoPrecision().name()).isEqualTo("GEO_PIN_CONFIRMED");

        EstablishmentRules.location(new LocationDraft("RJ", "3550308", "São Paulo", null, "Rua X", null, null, null,
                38.72, -9.14, null), false, errors);
        assertThat(errors).anyMatch(e -> e.contains("não é de um município de RJ")).anyMatch(e -> e.contains("fora do Brasil"));

        errors.clear();
        EstablishmentRules.Location home = EstablishmentRules.location(new LocationDraft("PE", "2611606", "Recife",
                "Boa Viagem", "Rua da Casa", "12", null, null, -8.1189, -34.9010, null), true, errors);
        assertThat(errors).isEmpty();
        assertThat(home.visibility()).isEqualTo(AddressVisibility.ADDR_PUBLIC_BAIRRO);
        EstablishmentRules.location(new LocationDraft("PE", "2611606", "Recife", null, "Rua da Casa", null, null, null,
                null, null, "ADDR_PUBLIC_FULL"), true, errors);
        assertThat(errors).containsExactly("Cozinha caseira mostra só o bairro: o endereço é a sua casa.");
        // ponto público: centro da célula de 0,005°, igual para quem vê
        assertThat(EstablishmentRules.publicCoordinate(-8.1189)).isEqualTo(-8.1175);
        assertThat(EstablishmentRules.publicCoordinate(-34.9010)).isEqualTo(-34.9025);
    }

    @Test
    void priceScaleDoublesFromTheReferenceMealAndRoundsToFiveReais() {
        PriceScale scale = new PriceScale(58, new double[]{0.7, 1.4, 2.8}, 5);
        assertThat(scale.thresholdsCents()).containsExactly(4000, 8000, 16000);
        assertThat(scale.levelOf(3999)).isEqualTo(PriceLevel.PRICE_1);
        assertThat(scale.levelOf(4000)).isEqualTo(PriceLevel.PRICE_2);     // [início, fim): R$ 40,00 já é $$
        assertThat(scale.levelOf(7999)).isEqualTo(PriceLevel.PRICE_2);
        assertThat(scale.levelOf(8000)).isEqualTo(PriceLevel.PRICE_3);
        assertThat(scale.levelOf(16000)).isEqualTo(PriceLevel.PRICE_4);
        assertThat(scale.rangeLabel(PriceLevel.PRICE_2)).isEqualTo("R$ 40 a R$ 80");
        assertThat(scale.rangeLabel(PriceLevel.PRICE_4)).isEqualTo("acima de R$ 160");
        // reajuste: a mesma conta com a referência nova
        assertThat(new PriceScale(64, new double[]{0.7, 1.4, 2.8}, 5).thresholdsCents()).containsExactly(4500, 9000, 18000);

        List<String> errors = new ArrayList<>();
        EstablishmentRules.price(EstablishmentGroup.RESTAURANTE, new PriceDraft(null, null, true), errors);
        assertThat(errors).anyMatch(e -> e.contains("só para varejo"));
        errors.clear();
        EstablishmentRules.price(EstablishmentGroup.RESTAURANTE, new PriceDraft(9000, 6000, null), errors);
        assertThat(errors).containsExactly("O gasto máximo por pessoa é menor que o mínimo.");
    }

    @Test
    void starsAreIntegersAndDimensionsOnlyWhereTheyWereLived() {
        List<String> errors = new ArrayList<>();
        EstablishmentRules.review(new ReviewDraft(0, "DINE_IN", null, null, null), errors);
        assertThat(errors).anyMatch(e -> e.contains("1 (Péssimo) a 5 (Excelente)"));
        errors.clear();
        EstablishmentRules.review(new ReviewDraft(4, "DELIVERY", Map.of("AMBIENTE", 5, "ENTREGA", 2), null, null), errors);
        assertThat(errors).containsExactly("Ambiente não se avalia em \"Entrega\".");
        errors.clear();
        EstablishmentRules.ReviewContent ok = EstablishmentRules.review(
                new ReviewDraft(5, "DINE_IN", Map.of("AMBIENTE", 4, "COMIDA", 5), 6500, "  Moqueca boa  "), errors);
        assertThat(errors).isEmpty();
        assertThat(ok.body()).isEqualTo("Moqueca boa");
        assertThat(ok.dimensions()).containsEntry(ReviewDimension.AMBIENTE, 4);
    }

    @Test
    void ratingIsNoneThenFewThenAWeightedAverageAndRankingIsBayesian() {
        RatingCalculator calc = new RatingCalculator(4.2, 10, 5, 365, 0.25, CLOCK);
        assertThat(calc.summarize(List.of()).state()).isEqualTo(RatingCalculator.State.RATING_NONE);
        assertThat(calc.summarize(List.of()).average()).isNull();       // nunca 0
        RatingCalculator.Summary few = calc.summarize(List.of(sample(5, 0), sample(5, 0)));
        assertThat(few.state()).isEqualTo(RatingCalculator.State.RATING_FEW);
        assertThat(few.average()).isNull();
        assertThat(few.accessibleLabel()).isEqualTo("2 avaliações, ainda sem nota média");

        RatingCalculator.Summary rated = calc.summarize(List.of(sample(5, 0), sample(4, 0), sample(4, 0),
                sample(5, 0), sample(3, 0)));
        assertThat(rated.state()).isEqualTo(RatingCalculator.State.RATED);
        assertThat(rated.average()).isEqualByComparingTo(new BigDecimal("4.2"));
        assertThat(rated.averageLabel()).isEqualTo("4,2");
        assertThat(rated.histogram()).containsExactly(Map.entry(5, 2), Map.entry(4, 2), Map.entry(3, 1),
                Map.entry(2, 0), Map.entry(1, 0));
        assertThat(rated.accessibleLabel()).isEqualTo("Nota 4,2 de 5, com base em 5 avaliações");

        // recência: a nota de 2 anos pesa 1/4 (meia-vida de 1 ano), a de 5 anos fica no piso de 25%
        RatingCalculator.Summary aged = calc.summarize(List.of(sample(1, 730), sample(5, 0), sample(5, 0),
                sample(5, 0), sample(5, 0)));
        assertThat(aged.average()).isEqualByComparingTo(new BigDecimal("4.8"));
        assertThat(calc.weight(sample(1, 365 * 5))).isEqualTo(0.6 * 0.25);

        // um 5,0 com 2 avaliações não passa à frente de um 4,6 com 400
        double twoFives = calc.summarize(List.of(sample(5, 0), sample(5, 0))).rankingScore();
        List<RatingCalculator.Sample> many = new ArrayList<>(Collections.nCopies(240, sample(5, 0)));
        many.addAll(Collections.nCopies(160, sample(4, 0)));
        assertThat(calc.summarize(many).rankingScore()).isGreaterThan(twoFives);

        // dimensão só com 5 notas nela
        RatingCalculator.Summary dims = calc.summarize(List.of(dim(5, 4), dim(5, 4), dim(5, 5), dim(5, 5), dim(5, null)));
        assertThat(dims.dimensions()).isEmpty();
    }

    // ------------------------------------------------------------ apoio

    private static List<String> typeErrors(UserCategoryEnum owner, String informal, String primary, List<String> secondary) {
        List<String> errors = new ArrayList<>();
        EstablishmentRules.types(owner, informal, primary, secondary, errors);
        return errors;
    }

    private static List<String> classErrors(EstablishmentGroup group, String cuisine, List<String> secondary,
                                            String format, String approach) {
        List<String> errors = new ArrayList<>();
        EstablishmentRules.classification(group, new ClassificationDraft(cuisine, secondary, format, null, approach, null),
                errors);
        return errors;
    }

    private static RatingCalculator.Sample sample(int rating, int daysAgo) {
        return new RatingCalculator.Sample(rating, 0.6, NOW.minus(Duration.ofDays(daysAgo)), Map.of());
    }

    private static RatingCalculator.Sample dim(int rating, Integer food) {
        return new RatingCalculator.Sample(rating, 0.6, NOW, food == null ? Map.of() : Map.of(ReviewDimension.COMIDA, food));
    }
}
