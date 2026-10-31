package com.friendlyeats.api;

import com.friendlyeats.application.account.AccountService;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.support.ApiClient;
import com.friendlyeats.support.CapturingNotifier;
import com.friendlyeats.support.PolyglotContainers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Estabelecimento (RF6), classificação (RF7) e avaliação com estrelas (RF8) ponta a ponta, com login real: página
 * pública só com o selo conferido, endereço da cozinha caseira escondido, uma avaliação por pessoa, nota agregada,
 * faixa de preço vinda das avaliações, quem não avalia, 18+ em bar e a eliminação da conta (RF5).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
class EstablishmentApiIT {

    static final String PASSWORD = "uma frase longa de teste";

    @LocalServerPort
    int port;

    @Autowired
    UserAccountRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    StringRedisTemplate redis;

    @Autowired
    AccountService accounts;

    final Map<Long, ApiClient> browsers = new HashMap<>();
    UserAccount restaurant;
    UserAccount market;
    UserAccount homeCook;
    UserAccount ana;
    UserAccount teen;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE TABLE user_account RESTART IDENTITY CASCADE");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        browsers.clear();
        restaurant = save(owner("Sabor Mineiro", "dono@sabormineiro.example", UserCategoryEnum.ESTABELECIMENTO,
                Map.of("cnpj", "11222333000181", "legalName", "Sabor Mineiro Ltda", "tradeName", "Sabor Mineiro",
                        "cnae", "5611-2/01", "city", "Belo Horizonte", "state", "MG", "representativeName", "Rita",
                        "representativeRole", "Sócia", "representativeEmail", "rita@sabormineiro.example")));
        market = save(owner("Empório Bom", "loja@emporio.example", UserCategoryEnum.VAREJO,
                Map.of("segment", "EMPORIO", "cnpj", "11444777000161", "legalName", "Empório Bom Ltda", "cnae",
                        "4729-6/99", "representativeName", "Lia", "representativeRole", "Sócia",
                        "representativeEmail", "lia@emporio.example")));
        homeCook = save(owner("Marmitas da Lu", "lu@example.com", UserCategoryEnum.ESTABELECIMENTO,
                Map.of("informalFormat", "COZINHA_CASEIRA", "cpf", "52998224725", "tradeName", "Marmitas da Lu",
                        "city", "Recife", "state", "PE")));
        ana = save(person("Ana Ribeiro", "ana@example.com", LocalDate.of(1994, 5, 2)));
        teen = save(person("Duda", "duda@example.com", LocalDate.now().minusYears(15)));
    }

    @Test
    void establishmentPageIsPublicOnlyWithTheOwnerSealAndHidesTheHomeKitchenStreet() {
        // cliente sem conta de estabelecimento não cadastra lugar
        assertThat(send(HttpMethod.POST, "/api/v1/establishments", ana, mineiro()).getStatusCode().value()).isEqualTo(403);

        ResponseEntity<Map> created = send(HttpMethod.POST, "/api/v1/establishments", restaurant, mineiro());
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        Map<String, Object> page = created.getBody();
        long id = ((Number) page.get("id")).longValue();
        assertThat(page).containsEntry("published", false).containsEntry("adultsOnly", false);
        assertThat((List<String>) page.get("publishBlockers")).anyMatch(b -> b.contains("conferência do selo"));
        assertThat(map(page, "group")).containsEntry("code", "RESTAURANTE");
        assertThat(map(map(page, "classification"), "chip")).containsEntry("code", "BRASILEIRA")
                .containsEntry("lucideIcon", "cooking-pot");
        assertThat(map(page, "price")).containsEntry("level", "PRICE_2").containsEntry("glyph", "$$")
                .containsEntry("source", "PRICE_SRC_DECLARED").containsEntry("rangeLabel", "R$ 40 a R$ 80 por pessoa");
        assertThat(map(page, "rating")).containsEntry("state", "RATING_NONE").containsEntry("average", null)
                .containsEntry("accessibleLabel", "Sem avaliações ainda");

        // antes do selo, quem não é dono não vê (nem logado)
        assertThat(new ApiClient(port).get("/api/v1/establishments/" + id).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/api/v1/establishments/" + id, ana, null).getStatusCode().value()).isEqualTo(404);

        verify(restaurant);
        Map<String, Object> pub = new ApiClient(port).get("/api/v1/establishments/" + id).getBody();
        assertThat(pub).containsEntry("published", null).containsEntry("publishBlockers", null);
        assertThat(map(pub, "location")).containsEntry("street", "Rua Sergipe").containsEntry("approximate", false);
        assertThat(map(pub, "owner")).containsEntry("verified", true);

        // classificação inválida volta com todos os erros juntos
        ResponseEntity<Map> bad = send(HttpMethod.PUT, "/api/v1/establishments/" + id + "/classification", restaurant,
                Map.of("primaryCuisine", "BRASILEIRA", "secondaryCuisines", List.of("MINEIRA"), "approach", "FUSION"));
        assertThat(bad.getStatusCode().value()).isEqualTo(422);
        assertThat((List<String>) bad.getBody().get("errors")).anyMatch(e -> e.contains("Mineira já inclui Brasileira"));
        ResponseEntity<Map> fusion = send(HttpMethod.PUT, "/api/v1/establishments/" + id + "/classification", restaurant,
                Map.of("primaryCuisine", "MINEIRA", "secondaryCuisines", List.of("JAPONESA"), "approach", "FUSION",
                        "dietaryOffers", Map.of("SEM_GLUTEN", "OFERECE")));
        assertThat(fusion.getStatusCode().value()).isEqualTo(200);
        assertThat(map(map(fusion.getBody(), "classification"), "approach")).containsEntry("code", "FUSION");

        // loja de varejo: conta de Varejo, tipo do grupo Varejo, preço "não se aplica" e classificação opcional
        Map<String, Object> shop = new LinkedHashMap<>(Map.of("name", "Empório Bom", "primaryType", "EMPORIO",
                "location", Map.of("uf", "PR", "municipalityIbge", "4106902", "city", "Curitiba", "street", "Rua XV",
                        "streetNumber", "300"),
                "price", Map.of("notApplicable", true)));
        Map<String, Object> shopPage = send(HttpMethod.POST, "/api/v1/establishments", market, shop).getBody();
        assertThat(map(shopPage, "price")).containsEntry("level", "PRICE_NA");
        assertThat(map(shopPage, "classification")).containsEntry("chip", null);
        // feira com "não se aplica" que vira restaurante precisa informar o gasto por pessoa
        long fair = ((Number) send(HttpMethod.POST, "/api/v1/establishments", restaurant, Map.of("name", "Feira da Praça",
                "primaryType", "FEIRA_LIVRE", "location", shop.get("location"), "price", Map.of("notApplicable", true)))
                .getBody().get("id")).longValue();
        ResponseEntity<Map> retyped = send(HttpMethod.PATCH, "/api/v1/establishments/" + fair, restaurant,
                Map.of("primaryType", "RESTAURANTE_A_LA_CARTE", "classification", Map.of("primaryCuisine", "BRASILEIRA")));
        assertThat(retyped.getStatusCode().value()).isEqualTo(422);
        assertThat((List<String>) retyped.getBody().get("errors")).anyMatch(e -> e.contains("só para varejo"));
        shop.put("primaryType", "BISTRO");
        assertThat(send(HttpMethod.POST, "/api/v1/establishments", market, shop).getStatusCode().value()).isEqualTo(422);

        // cozinha caseira com CPF: o público vê só o bairro e um ponto arredondado; um único ponto de venda
        Map<String, Object> kitchen = Map.of("name", "Marmitas da Lu", "primaryType", "MARMITARIA",
                "location", Map.of("uf", "PE", "municipalityIbge", "2611606", "city", "Recife",
                        "neighborhood", "Boa Viagem", "street", "Rua da Casa", "streetNumber", "12",
                        "latitude", -8.1189, "longitude", -34.9010),
                "price", Map.of("minCents", 2500, "maxCents", 3500),
                "classification", Map.of("primaryFormat", "PRATO_FEITO", "approach", "CASEIRA"));
        Map<String, Object> mine = send(HttpMethod.POST, "/api/v1/establishments", homeCook, kitchen).getBody();
        long kitchenId = ((Number) mine.get("id")).longValue();
        assertThat(map(mine, "location")).containsEntry("street", "Rua da Casa")
                .containsEntry("addressVisibility", "ADDR_PUBLIC_BAIRRO");
        assertThat(send(HttpMethod.POST, "/api/v1/establishments", homeCook, kitchen).getStatusCode().value()).isEqualTo(409);
        verify(homeCook);
        Map<String, Object> seen = map(new ApiClient(port).get("/api/v1/establishments/" + kitchenId).getBody(), "location");
        assertThat(seen).containsEntry("neighborhood", "Boa Viagem").containsEntry("street", null)
                .containsEntry("streetNumber", null).containsEntry("approximate", true)
                .containsEntry("latitude", -8.1175).containsEntry("longitude", -34.9025);

        // vários cadastros simultâneos do negócio com CPF: um único ponto de venda, mesmo na corrida
        UserAccount truck = save(owner("Taco do Zé", "taco@example.com", UserCategoryEnum.ESTABELECIMENTO,
                Map.of("informalFormat", "FOOD_TRUCK", "cpf", "11144477735", "tradeName", "Taco do Zé",
                        "city", "Curitiba", "state", "PR")));
        Map<String, Object> truckBody = Map.of("name", "Taco do Zé", "primaryType", "FOOD_TRUCK",
                "location", Map.of("uf", "PR", "municipalityIbge", "4106902", "city", "Curitiba", "street", "Praça Rui Barbosa"),
                "classification", Map.of("primaryFormat", "TACOS_BURRITOS"));
        List<ApiClient> tabs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ApiClient tab = new ApiClient(port);
            assertThat(tab.login(truck.getEmail(), PASSWORD).getStatusCode().value()).isEqualTo(200);
            tab.get("/api/v1/auth/csrf");
            tabs.add(tab);
        }
        assertThat(tabs.parallelStream().map(tab -> tab.send(HttpMethod.POST, "/api/v1/establishments", truckBody)
                .getStatusCode().value()).toList()).isSubsetOf(201, 409).containsOnlyOnce(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM establishment WHERE owner_id = ?", Integer.class,
                truck.getId())).isEqualTo(1);

        // a conta passa a ser cozinha caseira depois: o endereço completo já gravado deixa de ser público
        jdbc.update("UPDATE user_account SET type_details = '{\"informalFormat\":\"COZINHA_CASEIRA\",\"cpf\":"
                + "\"52998224725\",\"tradeName\":\"Sabor Mineiro\",\"city\":\"Belo Horizonte\",\"state\":\"MG\"}' "
                + "WHERE id = ?", restaurant.getId());
        assertThat(map(new ApiClient(port).get("/api/v1/establishments/" + id).getBody(), "location"))
                .containsEntry("street", null).containsEntry("addressVisibility", "ADDR_PUBLIC_BAIRRO")
                .containsEntry("approximate", true);

        // dono fecha o lugar de vez; a página fica para o histórico
        Map<String, Object> closed = send(HttpMethod.PATCH, "/api/v1/establishments/" + kitchenId, homeCook,
                Map.of("status", "CLOSED")).getBody();
        assertThat(closed).containsEntry("status", "CLOSED");
        assertThat(send(HttpMethod.PATCH, "/api/v1/establishments/" + kitchenId, ana, Map.of("name", "X"))
                .getStatusCode().value()).isEqualTo(403);

        assertThat(new ApiClient(port).get("/api/v1/taxonomy").getBody()).containsKeys("cuisines", "formats",
                "establishmentTypes", "priceLevels", "cuisineChips");
    }

    @Test
    void reviewsAreOnePerPersonEditableAndFeedTheRatingAndThePrice() {
        long id = publishedMineiro();
        String reviews = "/api/v1/establishments/" + id + "/reviews";

        ResponseEntity<Map> first = send(HttpMethod.PUT, reviews + "/me", ana,
                Map.of("rating", 3, "visitMode", "DINE_IN", "dimensions", Map.of("COMIDA", 4, "AMBIENTE", 3),
                        "spentPerPersonCents", 9000, "body", "Pão de queijo bom, fila longa."));
        assertThat(first.getStatusCode().value()).isEqualTo(201);
        assertThat(first.getBody()).containsEntry("ratingLabel", "Regular").containsEntry("edited", false);

        // a mesma pessoa edita: substitui (200) e a versão anterior fica no histórico
        ResponseEntity<Map> edited = send(HttpMethod.PUT, reviews + "/me", ana,
                Map.of("rating", 5, "visitMode", "DINE_IN", "spentPerPersonCents", 9000, "body", "Voltei: excelente."));
        assertThat(edited.getStatusCode().value()).isEqualTo(200);
        assertThat(edited.getBody()).containsEntry("rating", 5).containsEntry("edited", true);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review WHERE establishment_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT rating FROM review_revision", Integer.class)).isEqualTo(3);

        // dimensões só onde fazem sentido; nota fora de 1 a 5 é recusada
        ResponseEntity<Map> wrong = send(HttpMethod.PUT, reviews + "/me", ana,
                Map.of("rating", 6, "visitMode", "DELIVERY", "dimensions", Map.of("AMBIENTE", 4)));
        assertThat(wrong.getStatusCode().value()).isEqualTo(422);
        assertThat((List<String>) wrong.getBody().get("errors")).hasSize(2);

        // de 1 a 4 avaliações não há média; a partir de 5, há
        assertThat(map(new ApiClient(port).get("/api/v1/establishments/" + id).getBody(), "rating"))
                .containsEntry("state", "RATING_FEW").containsEntry("average", null);
        int[] stars = {4, 4, 5, 3};
        for (int i = 0; i < stars.length; i++) {
            UserAccount p = save(person("Pessoa " + i, "p" + i + "@example.com", LocalDate.of(1990, 1, 1)));
            assertThat(send(HttpMethod.PUT, reviews + "/me", p, Map.of("rating", stars[i], "visitMode", "DINE_IN",
                    "spentPerPersonCents", 9000 + i * 500)).getStatusCode().value()).isEqualTo(201);
        }
        Map<String, Object> page = new ApiClient(port).get("/api/v1/establishments/" + id).getBody();
        assertThat(map(page, "rating")).containsEntry("state", "RATED").containsEntry("count", 5)
                .containsEntry("averageLabel", "4,2")
                .containsEntry("accessibleLabel", "Nota 4,2 de 5, com base em 5 avaliações");
        assertThat(map(map(page, "rating"), "histogram")).containsEntry("5", 2).containsEntry("4", 2).containsEntry("3", 1);
        // 5 relatos de gasto (mediana R$ 95) ganham do declarado (R$ 45–65): a faixa passa a $$$
        assertThat(map(page, "price")).containsEntry("level", "PRICE_3").containsEntry("source", "PRICE_SRC_REVIEWS")
                .containsEntry("reportCount", 5);

        Map<String, Object> list = new ApiClient(port).get(reviews + "?size=2").getBody();
        assertThat(list).containsEntry("total", 5);
        assertThat((List<?>) list.get("items")).hasSize(2);

        // conta em exclusão some do agregado e da lista, sem perder a avaliação
        jdbc.update("UPDATE user_account SET account_status = 'PENDING_DELETION' WHERE id = ?", ana.getId());
        assertThat(map(new ApiClient(port).get(reviews).getBody(), "rating")).containsEntry("count", 4)
                .containsEntry("state", "RATING_FEW");
        jdbc.update("UPDATE user_account SET account_status = 'ACTIVE' WHERE id = ?", ana.getId());

        // a mesma avaliação enviada várias vezes ao mesmo tempo: uma linha só, e nunca 500
        UserAccount eager = save(person("Apressada", "eager@example.com", LocalDate.of(1992, 2, 2)));
        List<ApiClient> tabs = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            ApiClient tab = new ApiClient(port);
            assertThat(tab.login(eager.getEmail(), PASSWORD).getStatusCode().value()).isEqualTo(200);
            tab.get("/api/v1/auth/csrf");
            tabs.add(tab);
        }
        List<Integer> statuses = tabs.parallelStream()
                .map(tab -> tab.send(HttpMethod.PUT, reviews + "/me", Map.of("rating", 4, "visitMode", "DINE_IN"))
                        .getStatusCode().value())
                .toList();
        assertThat(statuses).isSubsetOf(200, 201, 409).containsOnlyOnce(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review WHERE author_id = ?", Integer.class, eager.getId()))
                .isEqualTo(1);

        // removida pela moderação: o autor não reescreve nem apaga para recomeçar
        jdbc.update("UPDATE review SET status = 'REMOVED' WHERE author_id = ?", eager.getId());
        assertThat(sendAs(tabs.getFirst(), HttpMethod.DELETE, reviews + "/me")).isEqualTo(409);
        assertThat(tabs.getFirst().send(HttpMethod.PUT, reviews + "/me", Map.of("rating", 5, "visitMode", "DINE_IN"))
                .getStatusCode().value()).isEqualTo(409);

        assertThat(send(HttpMethod.DELETE, reviews + "/me", ana, null).getStatusCode().value()).isEqualTo(204);
        assertThat(send(HttpMethod.GET, reviews + "/me", ana, null).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void organizationsAndOwnersDoNotReviewAndBarsAreForAdults() {
        long id = publishedMineiro();
        Map<String, Object> five = Map.of("rating", 5, "visitMode", "DINE_IN");
        assertThat(send(HttpMethod.PUT, "/api/v1/establishments/" + id + "/reviews/me", restaurant, five)
                .getBody().get("detail")).isEqualTo("Você não pode avaliar o próprio estabelecimento.");
        assertThat(send(HttpMethod.PUT, "/api/v1/establishments/" + id + "/reviews/me", market, five)
                .getStatusCode().value()).isEqualTo(403);
        UserAccount unverified = save(new UserAccount("Sem e-mail", "nao@example.com", passwords.encode(PASSWORD),
                UserCategoryEnum.COMENSAL));
        assertThat(send(HttpMethod.PUT, "/api/v1/establishments/" + id + "/reviews/me", unverified, five)
                .getStatusCode().value()).isEqualTo(403);

        // boteco: login obrigatório e só 18+
        UserAccount pub = save(owner("Boteco do Zé", "ze@boteco.example", UserCategoryEnum.ESTABELECIMENTO,
                Map.of("cnpj", "11222333000262", "legalName", "Boteco do Zé Ltda", "tradeName", "Boteco do Zé",
                        "cnae", "5611-2/04", "city", "Rio de Janeiro", "state", "RJ", "representativeName", "Zé",
                        "representativeRole", "Sócio", "representativeEmail", "ze@boteco.example")));
        long bar = ((Number) send(HttpMethod.POST, "/api/v1/establishments", pub, Map.of("name", "Boteco do Zé",
                "primaryType", "BOTECO",
                "location", Map.of("uf", "RJ", "municipalityIbge", "3304557", "city", "Rio de Janeiro",
                        "street", "Rua do Ouvidor", "streetNumber", "10"),
                "classification", Map.of("primaryFormat", "PETISCOS_BOTECO", "secondaryFormats", List.of("CERVEJA"))))
                .getBody().get("id")).longValue();
        verify(pub);
        ResponseEntity<Map> anonymous = new ApiClient(port).get("/api/v1/establishments/" + bar);
        assertThat(anonymous.getStatusCode().value()).isEqualTo(401);
        assertThat((String) anonymous.getBody().get("detail")).contains("maiores de 18");
        assertThat(send(HttpMethod.GET, "/api/v1/establishments/" + bar, teen, null).getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.PUT, "/api/v1/establishments/" + bar + "/reviews/me", teen, five)
                .getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.GET, "/api/v1/establishments/" + bar, ana, null).getBody())
                .containsEntry("adultsOnly", true);
    }

    @Test
    void accountEliminationRemovesReviewsAndOwnedEstablishments() {
        long id = publishedMineiro();
        assertThat(send(HttpMethod.PUT, "/api/v1/establishments/" + id + "/reviews/me", ana,
                Map.of("rating", 4, "visitMode", "TAKEOUT")).getStatusCode().value()).isEqualTo(201);

        jdbc.update("UPDATE user_account SET account_status = 'PENDING_DELETION', "
                + "deletion_scheduled_for = now() - interval '1 minute' WHERE id IN (?, ?)", ana.getId(), restaurant.getId());
        accounts.eliminate(ana.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review", Integer.class)).isZero();
        accounts.eliminate(restaurant.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM establishment", Integer.class)).isZero();
    }

    /** RF11: horários, fechamento temporário, cobranças, facetas e botões; só quem edita muda; tudo volta junto no 422. */
    @Test
    void placePageHasHoursChargesFacetsAndActionButtonsEditableOnlyByTheOwner() {
        Map<String, Object> draft = new LinkedHashMap<>(mineiro());
        Map<String, Object> weekly = new LinkedHashMap<>();
        for (String day : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            weekly.put(day, List.of(Map.of("opens", "00:00", "closes", "00:00")));
        }
        draft.put("hours", Map.of("weekly", weekly, "special", List.of(
                Map.of("date", LocalDate.now().plusDays(10).toString(), "closed", true, "note", "Inventário"))));
        draft.put("facets", Map.of("WIFI", "SIM", "PET_FRIENDLY", "SO_AREA_EXTERNA", "PIX", "SIM",
                "MUSICA_AO_VIVO", "SIM", "ENTRADA_ACESSIVEL", "PARCIAL"));
        draft.put("charges", Map.of("serviceFeePercent", 10,
                "couvert", Map.of("kind", "ARTISTICO", "cents", 1500, "days", List.of("FRIDAY", "SATURDAY")),
                "paymentAdjustments", List.of(Map.of("method", "PIX", "kind", "DESCONTO", "percent", 5))));
        draft.put("actions", Map.of("links", List.of(
                Map.of("provider", "IFOOD", "url", "https://www.ifood.com.br/delivery/bh/sabor-mineiro", "preferred", true),
                Map.of("provider", "THEFORK", "url", "https://www.thefork.com.br/restaurante/sabor-mineiro")),
                "whatsapp", "(31) 99999-8888"));
        ResponseEntity<Map> created = send(HttpMethod.POST, "/api/v1/establishments", restaurant, draft);
        assertThat(created.getStatusCode().value()).isEqualTo(201);
        long id = ((Number) created.getBody().get("id")).longValue();
        verify(restaurant);

        Map<String, Object> pub = new ApiClient(port).get("/api/v1/establishments/" + id).getBody();
        assertThat(pub).containsEntry("viewerCanEdit", false);
        assertThat(map(pub, "openStatus")).containsEntry("state", "OPEN").containsEntry("label", "Aberto 24 horas")
                .containsEntry("timeZone", "America/Sao_Paulo");
        Map<String, Object> hours = map(pub, "hours");
        assertThat(hours).containsEntry("informed", true);
        assertThat((List<Map<String, Object>>) hours.get("weekly")).hasSize(7);
        assertThat((List<Map<String, Object>>) hours.get("special")).singleElement()
                .satisfies(sd -> assertThat(sd).containsEntry("closed", true).containsEntry("note", "Inventário"));
        Map<String, Object> charges = map(pub, "charges");
        assertThat((List<Map<String, Object>>) charges.get("lines")).extracting(l -> l.get("text")).containsExactly(
                "Taxa de serviço de 10% (opcional)",
                "Couvert artístico de R$ 15 por pessoa, só nos dias com apresentação ao vivo (sexta, sábado)",
                "5% de desconto no Pix");
        assertThat((String) charges.get("declaredLabel")).startsWith("Declarado pelo estabelecimento em ");
        assertThat((String) charges.get("notice")).contains("Decreto 5.903/2006");
        List<Map<String, Object>> facets = (List<Map<String, Object>>) pub.get("facets");
        assertThat(facets).extracting(f -> f.get("code"))
                .containsExactly("ATENDIMENTO", "REFEICOES", "AMBIENTE", "ACESSIBILIDADE", "PAGAMENTO");
        assertThat(facets.get(3).get("items").toString()).contains("CAO_ASSISTENCIA", "Garantido por lei", "PARCIAL");
        assertThat(facets.get(0).get("items").toString()).contains("DELIVERY_APP", "Pelos links de delivery");
        List<Map<String, Object>> actions = (List<Map<String, Object>>) pub.get("actions");
        assertThat(actions).extracting(a -> a.get("label"))
                .containsExactly("Reservar pelo TheFork", "Pedir pelo iFood", "Conversar no WhatsApp");
        assertThat(actions.get(2)).containsEntry("url", "https://wa.me/5531999998888").containsEntry("phone", "+5531999998888");
        assertThat(actions.get(1).get("exitNotice")).isEqualTo("Você vai sair do Friendly Eats. Pedido e pagamento são com iFood.");
        assertThat(send(HttpMethod.GET, "/api/v1/establishments/" + id, restaurant, null).getBody())
                .containsEntry("viewerCanEdit", true);

        // o card da busca mostra o mesmo "aberto agora"
        Map<String, Object> card = ((List<Map<String, Object>>) new ApiClient(port).get("/api/v1/establishments?q=Sabor")
                .getBody().get("items")).getFirst();
        assertThat(map(card, "openStatus")).containsEntry("open", true);

        // só o dono edita: a outra conta de estabelecimento recebe 404
        assertThat(send(HttpMethod.PATCH, "/api/v1/establishments/" + id, market,
                Map.of("temporaryClosure", Map.of("closed", true))).getStatusCode().value()).isEqualTo(404);

        // tudo errado de uma vez: 422 com todos os erros e nada gravado
        ResponseEntity<Map> bad = send(HttpMethod.PATCH, "/api/v1/establishments/" + id, restaurant, Map.of(
                "facets", Map.of("CAO_ASSISTENCIA", "NAO", "MUSICA_AO_VIVO", "NAO"),
                "charges", Map.of("serviceFeePercent", 30, "minimumSpendCents", 5000),
                "actions", Map.of("links", List.of(Map.of("provider", "IFOOD", "url", "https://ifood.golpe.example/x"))),
                "hours", Map.of("weekly", Map.of("MONDAY", List.of(Map.of("opens", "11:00", "closes", "15:00"),
                        Map.of("opens", "14:00", "closes", "18:00"))))));
        assertThat(bad.getStatusCode().value()).isEqualTo(422);
        assertThat((List<String>) bad.getBody().get("errors"))
                .anyMatch(e -> e.contains("garantido por lei"))
                .anyMatch(e -> e.startsWith("Consumação mínima não é aceita"))
                .anyMatch(e -> e.startsWith("Taxa de serviço de 1% a 20%"))
                .anyMatch(e -> e.startsWith("Link do iFood"))
                .anyMatch(e -> e.contains("se sobrepõem"));
        // mudar a faceta sem mexer nas cobranças também confere o couvert artístico gravado
        assertThat((List<String>) send(HttpMethod.PATCH, "/api/v1/establishments/" + id, restaurant,
                Map.of("facets", Map.of("MUSICA_AO_VIVO", "NAO"))).getBody().get("errors"))
                .anyMatch(e -> e.contains("\"Música ao vivo\" está como Não"));

        // pausa com data de volta: some o "aberto agora"
        String back = LocalDate.now().plusDays(20).toString();
        Map<String, Object> paused = send(HttpMethod.PATCH, "/api/v1/establishments/" + id, restaurant,
                Map.of("temporaryClosure", Map.of("closed", true, "reopensOn", back))).getBody();
        assertThat(map(paused, "openStatus")).containsEntry("state", "TEMPORARILY_CLOSED").containsEntry("open", false);
        assertThat(map(map(paused, "hours"), "temporaryClosure")).containsEntry("reopensOn", back);
        assertThat(jdbc.queryForObject("SELECT temporarily_closed FROM establishment WHERE id = ?", Boolean.class, id))
                .isTrue();
        // tirar as cobranças limpa a data de declaração
        Map<String, Object> noCharges = send(HttpMethod.PATCH, "/api/v1/establishments/" + id, restaurant,
                Map.of("charges", Map.of(), "temporaryClosure", Map.of("closed", false))).getBody();
        assertThat(map(noCharges, "charges")).containsEntry("declaredAt", null);
        assertThat((List<?>) map(noCharges, "charges").get("lines")).isEmpty();
        assertThat(map(noCharges, "openStatus")).containsEntry("state", "OPEN");
    }

    // ------------------------------------------------------------ apoio

    private static int sendAs(ApiClient tab, HttpMethod method, String path) {
        return tab.send(method, path, null).getStatusCode().value();
    }

    private long publishedMineiro() {
        long id = ((Number) send(HttpMethod.POST, "/api/v1/establishments", restaurant, mineiro()).getBody().get("id"))
                .longValue();
        verify(restaurant);
        return id;
    }

    private static Map<String, Object> mineiro() {
        return Map.of("name", "Sabor Mineiro", "primaryType", "RESTAURANTE_A_LA_CARTE",
                "secondaryTypes", List.of("RESTAURANTE_QUILO"),
                "location", Map.of("uf", "MG", "municipalityIbge", "3106200", "city", "Belo Horizonte",
                        "neighborhood", "Savassi", "street", "Rua Sergipe", "streetNumber", "1000",
                        "cep", "30130-171", "latitude", -19.9370, "longitude", -43.9330),
                "price", Map.of("minCents", 4500, "maxCents", 6500),
                "classification", Map.of("primaryCuisine", "MINEIRA", "primaryFormat", "PRATO_FEITO",
                        "secondaryFormats", List.of("SALGADOS")));
    }

    /** A moderação conferiu o selo da conta (RF1); a moderação por API ainda é planejada (RF13). */
    private void verify(UserAccount owner) {
        jdbc.update("UPDATE user_account SET category_verification = ? WHERE id = ?",
                CategoryVerification.VERIFIED.name(), owner.getId());
    }

    private UserAccount owner(String name, String email, UserCategoryEnum type, Map<String, Object> details) {
        UserAccount u = new UserAccount(name, email, passwords.encode(PASSWORD), type);
        u.setUserDateOfBirth(LocalDate.of(1980, 1, 1));
        u.setTypeDetails(details);
        u.setCategoryVerification(CategoryVerification.PENDING);
        u.activate();
        return u;
    }

    private UserAccount person(String name, String email, LocalDate birth) {
        UserAccount u = new UserAccount(name, email, passwords.encode(PASSWORD), UserCategoryEnum.COMENSAL);
        u.setUserDateOfBirth(birth);
        u.activate();
        return u;
    }

    private UserAccount save(UserAccount user) {
        return tx.execute(s -> users.saveAndFlush(user));
    }

    private ResponseEntity<Map> send(HttpMethod method, String path, UserAccount who, Object body) {
        ApiClient browser = browsers.computeIfAbsent(who.getId(), id -> {
            ApiClient b = new ApiClient(port);
            assertThat(b.login(who.getEmail(), PASSWORD).getStatusCode().value()).isEqualTo(200);
            return b;
        });
        return browser.send(method, path, body);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }
}
