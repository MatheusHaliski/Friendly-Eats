package com.friendlyeats.api;

import com.friendlyeats.application.account.AccountService;
import com.friendlyeats.application.media.MediaStorage;
import com.friendlyeats.application.post.TrendingService;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.support.ApiClient;
import com.friendlyeats.support.CapturingNotifier;
import com.friendlyeats.support.PolyglotContainers;
import org.junit.jupiter.api.BeforeEach;
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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base dos testes de integração de posts (RF9, RF12, RF14, RF15): contas de cada tipo com login real, um restaurante
 * e um boteco no ar, e o envio de foto direto ao Garage.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
abstract class PostItBase {

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
    MediaStorage storage;

    @Autowired
    AccountService accounts;

    @Autowired
    TrendingService trending;

    final Map<Long, ApiClient> browsers = new HashMap<>();
    UserAccount ana;          // comensal adulta
    UserAccount teen;         // comensal de 15 anos (perfil privado travado)
    UserAccount minor17;      // comensal de 17 anos (perfil público, mas sem 18+)
    UserAccount creator;
    UserAccount chef;
    UserAccount community;
    UserAccount restaurant;   // ESTABELECIMENTO com selo
    UserAccount market;       // VAREJO com selo
    UserAccount official;     // OFICIAL com selo
    UserAccount pendingOrg;   // ESTABELECIMENTO com selo em conferência
    long mineiro;             // lugar do restaurant
    long boteco;              // lugar 18+ do restaurant
    long emporio;             // lugar do market

    @BeforeEach
    void seedAccounts() {
        jdbc.execute("TRUNCATE TABLE user_account RESTART IDENTITY CASCADE");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        browsers.clear();
        ana = save(person("Ana Ribeiro", "ana@example.com", UserCategoryEnum.COMENSAL, LocalDate.of(1994, 5, 2)));
        teen = save(person("Duda", "duda@example.com", UserCategoryEnum.COMENSAL, LocalDate.now().minusYears(15)));
        minor17 = save(person("Léo", "leo@example.com", UserCategoryEnum.COMENSAL,
                LocalDate.now().minusYears(17)));
        creator = save(person("Gula Crítica", "gula@example.com", UserCategoryEnum.CRIADOR, LocalDate.of(1988, 3, 3)));
        chef = save(person("Rita Duarte", "rita@example.com", UserCategoryEnum.CHEF, LocalDate.of(1985, 1, 1)));
        community = save(person("Confraria do Pão", "pao@example.com", UserCategoryEnum.COMUNIDADE,
                LocalDate.of(1980, 1, 1)));
        restaurant = save(org("Sabor Mineiro", "dono@sabormineiro.example", UserCategoryEnum.ESTABELECIMENTO,
                CategoryVerification.VERIFIED, Map.of("city", "Belo Horizonte", "state", "MG")));
        market = save(org("Empório Bom", "loja@emporio.example", UserCategoryEnum.VAREJO, CategoryVerification.VERIFIED,
                Map.of("city", "Curitiba", "state", "PR")));
        official = save(org("Vigilância de Teste", "alertas@vigilancia.example", UserCategoryEnum.OFICIAL,
                CategoryVerification.VERIFIED, Map.of("city", "São Paulo", "state", "SP")));
        pendingOrg = save(org("Ainda Sem Selo", "pend@org.example", UserCategoryEnum.ESTABELECIMENTO,
                CategoryVerification.PENDING, Map.of("city", "Recife", "state", "PE")));
        // preferências como o cadastro grava (RF2): a de 15 anos nasce privada e com comentários só de seguidores
        for (UserAccount u : List.of(ana, teen, minor17, creator, chef, community, restaurant, market, official)) {
            boolean minor = u == teen;
            jdbc.update("INSERT INTO user_preference (user_id, profile_private, who_can_comment) VALUES (?, ?, ?)",
                    u.getId(), minor, minor ? "FOLLOWERS" : "EVERYONE");
        }
        // RF17: os adultos que veem bebida nos testes têm a idade verificada; o restaurant (adulto) não tem
        for (UserAccount u : List.of(ana, creator, chef, community)) {
            com.friendlyeats.support.AgeFixtures.verifiedAdult(jdbc, u.getId());
        }
        mineiro = place(restaurant, Map.of("name", "Sabor Mineiro", "primaryType", "RESTAURANTE_A_LA_CARTE",
                "location", Map.of("uf", "MG", "municipalityIbge", "3106200", "city", "Belo Horizonte",
                        "street", "Rua Sergipe", "streetNumber", "1000"),
                "price", Map.of("minCents", 4500, "maxCents", 6500),
                "classification", Map.of("primaryCuisine", "MINEIRA", "primaryFormat", "PRATO_FEITO")));
        boteco = place(restaurant, Map.of("name", "Boteco do Zé", "primaryType", "BOTECO",
                "location", Map.of("uf", "MG", "municipalityIbge", "3106200", "city", "Belo Horizonte",
                        "street", "Rua do Ouvidor", "streetNumber", "10"),
                "classification", Map.of("primaryFormat", "PETISCOS_BOTECO")));
        emporio = place(market, Map.of("name", "Empório Bom", "primaryType", "EMPORIO",
                "location", Map.of("uf", "PR", "municipalityIbge", "4106902", "city", "Curitiba", "street", "Rua XV",
                        "streetNumber", "300"),
                "price", Map.of("notApplicable", true)));
    }

    // ------------------------------------------------------------ apoio

    long place(UserAccount owner, Map<String, Object> body) {
        ResponseEntity<Map> r = send(HttpMethod.POST, "/api/v1/establishments", owner, body);
        assertThat(r.getStatusCode().value()).as("cadastro de %s", body.get("name")).isEqualTo(201);
        return ((Number) r.getBody().get("id")).longValue();
    }

    /** Publica e devolve a resposta; o corpo é mutável para os testes acrescentarem campos. */
    ResponseEntity<Map> publish(UserAccount who, Map<String, Object> body) {
        return send(HttpMethod.POST, "/api/v1/posts", who, body);
    }

    String publishId(UserAccount who, Map<String, Object> body) {
        ResponseEntity<Map> r = publish(who, body);
        assertThat(r.getStatusCode().value()).as("publicar %s: %s", body.get("kind"), r.getBody()).isEqualTo(201);
        return (String) r.getBody().get("id");
    }

    static Map<String, Object> post(String kind, String title, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("title", title);
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    /** Pede o tíquete e envia o arquivo direto ao bucket; devolve a chave em {@code uploads/}. */
    String uploadPhoto(UserAccount who, byte[] bytes, String contentType) throws Exception {
        Map<String, Object> ticket = send(HttpMethod.POST, "/api/v1/media/uploads", who,
                Map.of("kind", "IMAGE", "contentType", contentType, "sizeBytes", bytes.length)).getBody();
        assertThat(putToBucket(ticket, bytes)).isEqualTo(200);
        return (String) ticket.get("objectKey");
    }

    @SuppressWarnings("unchecked")
    static int putToBucket(Map<String, Object> ticket, byte[] bytes) throws Exception {
        HttpRequest.Builder put = HttpRequest.newBuilder(URI.create((String) ticket.get("uploadUrl")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        ((Map<String, String>) ticket.get("headers")).forEach((name, value) -> {
            if (!name.equalsIgnoreCase("content-length")) {
                put.header(name, value);
            }
        });
        return HttpClient.newHttpClient().send(put.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    UserAccount person(String name, String email, UserCategoryEnum type, LocalDate birth) {
        UserAccount u = new UserAccount(name, email, passwords.encode(PASSWORD), type);
        u.setUserDateOfBirth(birth);
        u.activate();
        return u;
    }

    UserAccount org(String name, String email, UserCategoryEnum type, CategoryVerification seal,
                    Map<String, Object> details) {
        UserAccount u = new UserAccount(name, email, passwords.encode(PASSWORD), type);
        u.setUserDateOfBirth(LocalDate.of(1980, 1, 1));
        u.setTypeDetails(details);
        u.setCategoryVerification(seal);
        u.activate();
        return u;
    }

    UserAccount save(UserAccount user) {
        return tx.execute(s -> users.saveAndFlush(user));
    }

    ResponseEntity<Map> get(String path, UserAccount who) {
        return send(HttpMethod.GET, path, who, null);
    }

    ResponseEntity<Map> send(HttpMethod method, String path, UserAccount who, Object body) {
        ApiClient browser = who == null ? new ApiClient(port) : browsers.computeIfAbsent(who.getId(), id -> {
            ApiClient b = new ApiClient(port);
            assertThat(b.login(who.getEmail(), PASSWORD).getStatusCode().value()).isEqualTo(200);
            return b;
        });
        return browser.send(method, path, body);
    }

    static int status(ResponseEntity<?> r) {
        return r.getStatusCode().value();
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Map<?, ?> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> list(Map<?, ?> parent, String key) {
        return (List<Map<String, Object>>) parent.get(key);
    }

    static List<Object> titles(Map<?, ?> page) {
        return list(page, "items").stream().map(i -> i.get("title")).toList();
    }

    static String errors(ResponseEntity<Map> r) {
        return String.valueOf(r.getBody().get("errors"));
    }
}
