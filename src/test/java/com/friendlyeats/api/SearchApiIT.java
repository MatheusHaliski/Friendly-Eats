package com.friendlyeats.api;

import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.AddressVisibility;
import com.friendlyeats.domain.taxonomy.Approach;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.GeoPrecision;
import com.friendlyeats.domain.taxonomy.VisitMode;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
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
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Busca e filtros (RF10) contra o PostGIS real: só páginas públicas, culinária com descendentes, filtros da
 * taxonomia, raio com distância aproximada para a cozinha caseira, 18+, ordenações e o card igual à página.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
class SearchApiIT {

    static final String PASSWORD = "uma frase longa de teste";
    /** Praça da Sé, São Paulo: o "eu" da busca por raio. */
    static final double ME_LAT = -23.5505;
    static final double ME_LNG = -46.6333;

    @LocalServerPort
    int port;

    @Autowired
    UserAccountRepository users;

    @Autowired
    EstablishmentRepository establishments;

    @Autowired
    ReviewRepository reviews;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TransactionTemplate tx;

    @Autowired
    PasswordEncoder passwords;

    @Autowired
    StringRedisTemplate redis;

    UserAccount owner;
    UserAccount homeOwner;
    long mineiro;      // ~0,5 km, MINEIRA, $$, 2 avaliações nota 5
    long baiano;       // ~3 km, BAIANA, vegano exclusivo, 20 avaliações média 4,6
    long cantina;      // ~8 km, ITALIANA + PIZZA, $$$
    long cozinha;      // ~1 km, cozinha caseira (ponto público arredondado)
    long boteco;       // ~0,8 km, BOTECO (18+)
    int people;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE TABLE user_account RESTART IDENTITY CASCADE");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        people = 0;
        owner = save(account("Rede Sabores", "rede@example.com", UserCategoryEnum.ESTABELECIMENTO, LocalDate.of(1980, 1, 1),
                Map.of("cnpj", "11222333000181"), CategoryVerification.VERIFIED));
        homeOwner = save(account("Marmitas da Lu", "lu@example.com", UserCategoryEnum.ESTABELECIMENTO, LocalDate.of(1980, 1, 1),
                Map.of("informalFormat", "COZINHA_CASEIRA", "cpf", "52998224725"), CategoryVerification.VERIFIED));
        UserAccount pending = save(account("Sem Selo", "semselo@example.com", UserCategoryEnum.ESTABELECIMENTO,
                LocalDate.of(1980, 1, 1), Map.of(), CategoryVerification.PENDING));

        mineiro = place(owner, "São Mineiro", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.MINEIRA, FoodFormat.PRATO_FEITO,
                Map.of(), 4500, 6500, -23.5460, ME_LNG, AddressVisibility.ADDR_PUBLIC_FULL);
        baiano = place(owner, "Dendê Vegano", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.BAIANA, null,
                Map.of(DietaryStyle.VEGANO, DietaryStyle.Level.EXCLUSIVO), 3000, 4000, -23.5235, ME_LNG,
                AddressVisibility.ADDR_PUBLIC_FULL);
        cantina = place(owner, "Cantina Nonna", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.ITALIANA, FoodFormat.PIZZA,
                Map.of(), 9000, 13000, -23.4785, ME_LNG, AddressVisibility.ADDR_PUBLIC_FULL);
        cozinha = place(homeOwner, "Marmitas da Lu", EstablishmentType.MARMITARIA, null, FoodFormat.PRATO_FEITO,
                Map.of(), 2000, 3000, -23.5413, -46.6341, AddressVisibility.ADDR_PUBLIC_BAIRRO);
        boteco = place(owner, "Boteco da Sé", EstablishmentType.BOTECO, null, FoodFormat.PETISCOS_BOTECO,
                Map.of(), 5000, 7000, -23.5433, ME_LNG, AddressVisibility.ADDR_PUBLIC_FULL);
        // fora da busca: conta sem selo, lugar fechado e endereço oculto (este só some do raio)
        place(pending, "Mineiro Sem Selo", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.MINEIRA, null, Map.of(),
                4500, 6500, -23.5460, ME_LNG, AddressVisibility.ADDR_PUBLIC_FULL);
        long closed = place(owner, "Mineiro Fechado", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.MINEIRA, null,
                Map.of(), 4500, 6500, -23.5460, ME_LNG, AddressVisibility.ADDR_PUBLIC_FULL);
        jdbc.update("UPDATE establishment SET status = 'CLOSED' WHERE id = ?", closed);
        place(owner, "Cozinha Fantasma", EstablishmentType.DARK_KITCHEN, Cuisine.CHINESA, null, Map.of(), 4000, 5000,
                -23.5500, ME_LNG, AddressVisibility.ADDR_HIDDEN);

        reviewsOf(mineiro, 5, 5);
        int[] many = new int[20];
        for (int i = 0; i < 20; i++) many[i] = i < 12 ? 5 : 4;      // 12×5 + 8×4 = 4,6
        reviewsOf(baiano, many);
    }

    @Test
    void searchShowsOnlyPublicPlacesAndCuisineIncludesItsDescendants() {
        ApiClient visitor = new ApiClient(port);
        Map<String, Object> all = visitor.get("/api/v1/establishments").getBody();
        // sem selo, fechado e boteco (visitante) ficam de fora; endereço oculto entra fora do raio
        assertThat(names(all)).containsExactlyInAnyOrder("São Mineiro", "Dendê Vegano", "Cantina Nonna",
                "Marmitas da Lu", "Cozinha Fantasma");
        assertThat(all).containsEntry("total", 5).containsEntry("radiusKm", null);

        assertThat(names(visitor.get("/api/v1/establishments?cuisine=BRASILEIRA").getBody()))
                .containsExactlyInAnyOrder("São Mineiro", "Dendê Vegano");
        assertThat(names(visitor.get("/api/v1/establishments?cuisine=NORDESTINA").getBody())).containsExactly("Dendê Vegano");
        assertThat(names(visitor.get("/api/v1/establishments?format=PIZZA&format=PRATO_FEITO").getBody()))
                .containsExactlyInAnyOrder("Cantina Nonna", "São Mineiro", "Marmitas da Lu");
        assertThat(names(visitor.get("/api/v1/establishments?dietary=VEGANO").getBody())).containsExactly("Dendê Vegano");
        // chip do card: o baiano vegano é Vegetal (não Brasileira); a marmitaria sem culinária é Brasileira pelo formato
        assertThat(names(visitor.get("/api/v1/establishments?chip=VEGETAL").getBody())).containsExactly("Dendê Vegano");
        assertThat(names(visitor.get("/api/v1/establishments?chip=BRASILEIRA").getBody()))
                .containsExactlyInAnyOrder("São Mineiro", "Marmitas da Lu");
        assertThat(names(visitor.get("/api/v1/establishments?chip=EUROPEIA,ASIATICA").getBody()))
                .containsExactlyInAnyOrder("Cantina Nonna", "Cozinha Fantasma");
        assertThat(names(visitor.get("/api/v1/establishments?q=sao min").getBody())).containsExactly("São Mineiro");
        assertThat(names(visitor.get("/api/v1/establishments?type=MARMITARIA,DARK_KITCHEN").getBody()))
                .containsExactlyInAnyOrder("Marmitas da Lu", "Cozinha Fantasma");
        // preço: declarado 45–65 → $$; 90–130 → $$$; o baiano tem 30–40 → $
        assertThat(names(visitor.get("/api/v1/establishments?price=PRICE_3").getBody())).containsExactly("Cantina Nonna");
        assertThat(names(visitor.get("/api/v1/establishments?price=PRICE_1").getBody()))
                .containsExactlyInAnyOrder("Dendê Vegano", "Marmitas da Lu");
        // nota mínima: só quem já tem média (5+ avaliações)
        assertThat(names(visitor.get("/api/v1/establishments?minRating=4.5").getBody())).containsExactly("Dendê Vegano");

        // o card mostra o mesmo que a página do lugar
        Map<String, Object> card = item(visitor.get("/api/v1/establishments?cuisine=BAIANA").getBody(), 0);
        Map<String, Object> page = visitor.get("/api/v1/establishments/" + baiano).getBody();
        assertThat(card.get("rating")).isEqualTo(page.get("rating"));
        assertThat(card.get("price")).isEqualTo(page.get("price"));
        assertThat(map(card, "chip")).containsEntry("code", "VEGETAL");
        assertThat(map(card, "rating")).containsEntry("averageLabel", "4,6");

        // 18+: o boteco só aparece para adulto logado com a idade verificada (RF17)
        assertThat(names(loggedIn("semverificar@example.com", LocalDate.of(1990, 1, 1))
                .get("/api/v1/establishments?type=BOTECO").getBody())).isEmpty();
        ApiClient adult = loggedIn("adulto@example.com", LocalDate.of(1990, 1, 1));
        com.friendlyeats.support.AgeFixtures.verifiedAdult(jdbc, users.findByEmail("adulto@example.com").orElseThrow().getId());
        assertThat(names(adult.get("/api/v1/establishments?type=BOTECO").getBody())).containsExactly("Boteco da Sé");
        assertThat(names(loggedIn("teen@example.com", LocalDate.now().minusYears(16))
                .get("/api/v1/establishments?type=BOTECO").getBody())).isEmpty();
    }

    @Test
    void radiusSearchMeasuresTheHomeKitchenFromItsPublicPointAndSortsByDistance() {
        ApiClient visitor = new ApiClient(port);
        String near = "/api/v1/establishments?lat=" + ME_LAT + "&lng=" + ME_LNG;
        Map<String, Object> five = visitor.get(near + "&sort=DISTANCE").getBody();
        // 5 km: o fantasma (oculto) e a cantina (8 km) ficam de fora
        assertThat(names(five)).containsExactly("São Mineiro", "Marmitas da Lu", "Dendê Vegano");
        assertThat(five).containsEntry("radiusKm", 5.0);
        assertThat(item(five, 0)).containsEntry("distanceKm", 0.5);
        Map<String, Object> home = item(five, 1);
        assertThat(map(home, "location")).containsEntry("approximate", true).containsEntry("neighborhood", "Sé");
        assertThat(((Number) home.get("distanceKm")).doubleValue() * 2 % 1).isZero();     // passos de 0,5 km

        assertThat(names(visitor.get(near + "&radiusKm=2").getBody())).containsExactlyInAnyOrder("São Mineiro", "Marmitas da Lu");
        assertThat(names(visitor.get(near + "&radiusKm=10").getBody())).contains("Cantina Nonna");

        // relevância: 20 avaliações com 4,6 passam à frente de 2 avaliações com 5,0 (bayesiano com m = 10)…
        assertThat(names(visitor.get("/api/v1/establishments?cuisine=BRASILEIRA").getBody()))
                .containsExactly("Dendê Vegano", "São Mineiro");
        // …mas, perto de mim, a distância também pesa: 0,5 km contra 3 km vira o jogo
        assertThat(names(visitor.get(near + "&cuisine=BRASILEIRA").getBody())).containsExactly("São Mineiro", "Dendê Vegano");
        // por nota: quem tem média vem antes de quem ainda não tem
        assertThat(names(visitor.get("/api/v1/establishments?sort=RATING&cuisine=BRASILEIRA").getBody()))
                .containsExactly("Dendê Vegano", "São Mineiro");

        ResponseEntity<Map> bad = visitor.get("/api/v1/establishments?lat=-23.5&sort=DISTANCE&radiusKm=80&cuisine=SUSHI");
        assertThat(bad.getStatusCode().value()).isEqualTo(422);
        assertThat((List<String>) bad.getBody().get("errors")).hasSize(3);    // lat sem lng, raio e culinária desconhecida
        assertThat(visitor.get("/api/v1/establishments?lat=38.7&lng=-9.1").getStatusCode().value()).isEqualTo(422);
        // NaN falha em toda comparação: a validação na forma positiva também o recusa
        assertThat(visitor.get(near + "&radiusKm=NaN").getStatusCode().value()).isEqualTo(422);
        assertThat(visitor.get("/api/v1/establishments?minRating=NaN").getStatusCode().value()).isEqualTo(422);

        Map<String, Object> paged = visitor.get("/api/v1/establishments?size=2&page=1").getBody();
        assertThat(paged).containsEntry("total", 5).containsEntry("page", 1);
        assertThat((List<?>) paged.get("items")).hasSize(2);
        assertThat(visitor.get("/api/v1/establishments?size=2&page=9").getBody()).containsEntry("total", 5);
    }

    // ------------------------------------------------------------ apoio

    private long place(UserAccount who, String name, EstablishmentType type, Cuisine cuisine, FoodFormat format,
                       Map<DietaryStyle, DietaryStyle.Level> dietary, int minCents, int maxCents, double lat, double lng,
                       AddressVisibility visibility) {
        return tx.execute(s -> {
            Establishment e = new Establishment(who.getId(), name);
            e.setTypes(type, List.of());
            e.classify(cuisine, List.of(), format, List.of(), Approach.TRADICIONAL, dietary);
            e.setDeclaredPrice(minCents, maxCents, false);
            e.setLocation("SP", "3550308", "São Paulo", "Sé", "Praça da Sé", "1", null, null, lat, lng,
                    GeoPrecision.GEO_PIN_CONFIRMED, visibility);
            return establishments.saveAndFlush(e).getId();
        });
    }

    private void reviewsOf(long place, int... ratings) {
        for (int rating : ratings) {
            UserAccount p = save(account("Pessoa " + people, "p" + people++ + "@example.com", UserCategoryEnum.COMENSAL,
                    LocalDate.of(1990, 1, 1), Map.of(), CategoryVerification.NOT_REQUIRED));
            tx.executeWithoutResult(s -> {
                Review r = new Review(place, p.getId());
                r.write(rating, VisitMode.DINE_IN, Map.of(), null, null, Instant.now());
                reviews.saveAndFlush(r);
            });
        }
    }

    private ApiClient loggedIn(String email, LocalDate birth) {
        save(account("Visitante", email, UserCategoryEnum.COMENSAL, birth, Map.of(), CategoryVerification.NOT_REQUIRED));
        ApiClient c = new ApiClient(port);
        assertThat(c.login(email, PASSWORD).getStatusCode().value()).isEqualTo(200);
        return c;
    }

    private UserAccount account(String name, String email, UserCategoryEnum type, LocalDate birth,
                                Map<String, Object> details, CategoryVerification seal) {
        UserAccount u = new UserAccount(name, email, passwords.encode(PASSWORD), type);
        u.setUserDateOfBirth(birth);
        u.setTypeDetails(details);
        u.setCategoryVerification(seal);
        u.activate();
        return u;
    }

    private UserAccount save(UserAccount user) {
        return tx.execute(s -> users.saveAndFlush(user));
    }

    @SuppressWarnings("unchecked")
    private static List<String> names(Map<String, Object> page) {
        return ((List<Map<String, Object>>) page.get("items")).stream().map(i -> (String) i.get("name")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> item(Map<String, Object> page, int i) {
        return ((List<Map<String, Object>>) page.get("items")).get(i);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }
}
