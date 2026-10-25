package com.friendlyeats.api;

import com.friendlyeats.domain.model.GeoLocation;
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

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Consentimentos (RF3) e gosto no perfil (RF2): chaves desligadas por padrão, histórico append-only, revogação com
 * efeito imediato (estilos alimentares e última localização apagados), bloqueio para menor de 16 e estilos
 * alimentares só com o consentimento sensível.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
class ConsentApiIT {

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

    final Map<Long, ApiClient> browsers = new HashMap<>();
    UserAccount ana;
    UserAccount teen;
    UserAccount older;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE TABLE user_account RESTART IDENTITY CASCADE");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        browsers.clear();
        ana = save(active("Ana Ribeiro", "ana@example.com", LocalDate.of(1994, 5, 2)));
        teen = save(active("Bia Souza", "bia@example.com", LocalDate.now().minusYears(14)));
        older = save(active("Caio Lima", "caio@example.com", LocalDate.now().minusYears(17)));
    }

    @Test
    void privacyByDefaultAndSensitiveDietaryStylesFollowTheConsent() {
        ResponseEntity<Map> list = send(HttpMethod.GET, "/api/v1/me/consents", ana, null);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        Map<String, Map<String, Object>> byPurpose = byPurpose(list.getBody());
        assertThat(byPurpose).containsOnlyKeys("LOCATION_TRACKING", "PERSONALIZED_FEED", "SENSITIVE_PROFILE_DATA",
                "MARKETING", "THIRD_PARTY_SHARING");
        assertThat(byPurpose.values()).allSatisfy(c -> {
            assertThat(c.get("granted")).isEqualTo(false);
            assertThat(c.get("lockedForMinor")).isEqualTo(false);
            assertThat((String) c.get("label")).isNotBlank();
        });
        assertThat(byPurpose.get("SENSITIVE_PROFILE_DATA")).containsEntry("sensitive", true)
                .containsEntry("legalBasis", "ART_11_I");

        // sem o consentimento sensível, estilos alimentares não entram no perfil; culinárias favoritas sim
        ResponseEntity<Map> denied = send(HttpMethod.PATCH, "/api/v1/me/profile", ana,
                Map.of("dietaryStyles", List.of("SEM_GLUTEN")));
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        ResponseEntity<Map> cuisines = send(HttpMethod.PATCH, "/api/v1/me/profile", ana,
                Map.of("favoriteCuisines", List.of("italiana", "JAPONESA", "ITALIANA")));
        assertThat(cuisines.getStatusCode().value()).isEqualTo(200);
        assertThat(cuisines.getBody().get("favoriteCuisines")).isEqualTo(List.of("ITALIANA", "JAPONESA"));
        assertThat(cuisines.getBody().get("dietaryStyles")).isEqualTo(List.of());

        ResponseEntity<Map> granted = send(HttpMethod.PUT, "/api/v1/me/consents/SENSITIVE_PROFILE_DATA", ana,
                Map.of("granted", true));
        assertThat(granted.getStatusCode().value()).isEqualTo(200);
        assertThat(granted.getBody()).containsEntry("granted", true).containsEntry("purpose", "SENSITIVE_PROFILE_DATA");
        assertThat(granted.getBody().get("updatedAt")).isNotNull();
        // repetir o mesmo valor não gera outro evento
        send(HttpMethod.PUT, "/api/v1/me/consents/SENSITIVE_PROFILE_DATA", ana, Map.of("granted", true));

        ResponseEntity<Map> styles = send(HttpMethod.PATCH, "/api/v1/me/profile", ana,
                Map.of("dietaryStyles", List.of("SEM_GLUTEN", "HALAL")));
        assertThat(styles.getStatusCode().value()).isEqualTo(200);
        assertThat(styles.getBody().get("dietaryStyles")).isEqualTo(List.of("SEM_GLUTEN", "HALAL"));

        // revogar apaga os estilos na hora (Art. 8º, §5º) e o perfil não aceita outros
        ResponseEntity<Map> revoked = send(HttpMethod.PUT, "/api/v1/me/consents/SENSITIVE_PROFILE_DATA", ana,
                Map.of("granted", false));
        assertThat(revoked.getBody()).containsEntry("granted", false);
        ResponseEntity<Map> profile = send(HttpMethod.GET, "/api/v1/me/profile", ana, null);
        assertThat(profile.getBody().get("dietaryStyles")).isEqualTo(List.of());
        assertThat(profile.getBody().get("favoriteCuisines")).isEqualTo(List.of("ITALIANA", "JAPONESA"));
        assertThat(send(HttpMethod.PATCH, "/api/v1/me/profile", ana, Map.of("dietaryStyles", List.of("HALAL")))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.PATCH, "/api/v1/me/profile", ana, Map.of("dietaryStyles", List.of()))
                .getStatusCode().value()).isEqualTo(200);

        // histórico append-only: ligou e desligou, com a base legal do Art. 11
        List<Map<String, Object>> events = jdbc.queryForList(
                "SELECT granted, legal_basis, channel FROM consent_event WHERE user_id = ? ORDER BY id", ana.getId());
        assertThat(events).extracting(e -> e.get("granted")).containsExactly(true, false);
        assertThat(events).extracting(e -> e.get("legal_basis")).containsOnly("ART_11_I");
        assertThat(events).extracting(e -> e.get("channel")).containsOnly("WEB");
    }

    @Test
    void revokingLocationTrackingErasesTheLastSavedLocation() {
        tx.executeWithoutResult(s -> {
            UserAccount u = users.findById(ana.getId()).orElseThrow();
            u.setLastSavedLocation(new GeoLocation(-23.56, -46.69, Instant.now()));
            users.saveAndFlush(u);
        });
        send(HttpMethod.PUT, "/api/v1/me/consents/LOCATION_TRACKING", ana, Map.of("granted", true));
        assertThat(users.findById(ana.getId()).orElseThrow().getLastSavedLocation()).isNotNull();

        send(HttpMethod.PUT, "/api/v1/me/consents/LOCATION_TRACKING", ana, Map.of("granted", false));
        assertThat(users.findById(ana.getId()).orElseThrow().getLastSavedLocation()).isNull();
    }

    @Test
    void minorsCannotTurnOnProfilingMarketingTrackingOrSensitiveData() {
        Map<String, Map<String, Object>> byPurpose = byPurpose(send(HttpMethod.GET, "/api/v1/me/consents", teen, null).getBody());
        assertThat(locked(byPurpose)).containsExactlyInAnyOrder("LOCATION_TRACKING", "PERSONALIZED_FEED", "MARKETING",
                "SENSITIVE_PROFILE_DATA");
        assertThat(send(HttpMethod.PUT, "/api/v1/me/consents/MARKETING", teen, Map.of("granted", true))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.PUT, "/api/v1/me/consents/SENSITIVE_PROFILE_DATA", teen, Map.of("granted", true))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(send(HttpMethod.PUT, "/api/v1/me/consents/MARKETING", teen, Map.of("granted", false))
                .getStatusCode().value()).isEqualTo(200);
        assertThat(send(HttpMethod.PUT, "/api/v1/me/consents/THIRD_PARTY_SHARING", teen, Map.of("granted", true))
                .getStatusCode().value()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consent_event WHERE user_id = ? AND granted",
                Integer.class, teen.getId())).isEqualTo(1);

        // aos 17: o ECA Digital já não trava, mas o dado sensível espera o consentimento do responsável (Art. 14)
        assertThat(locked(byPurpose(send(HttpMethod.GET, "/api/v1/me/consents", older, null).getBody())))
                .containsExactly("SENSITIVE_PROFILE_DATA");
        assertThat(send(HttpMethod.PUT, "/api/v1/me/consents/MARKETING", older, Map.of("granted", true))
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    void rejectsUnknownCodesAndAnonymousCalls() {
        assertThat(send(HttpMethod.GET, "/api/v1/me/consents", null, null).getStatusCode().value()).isEqualTo(401);
        assertThat(send(HttpMethod.PUT, "/api/v1/me/consents/VENDER_DADOS", ana, Map.of("granted", true))
                .getStatusCode().value()).isEqualTo(404);
        assertThat(send(HttpMethod.PUT, "/api/v1/me/consents/MARKETING", ana, Map.of())
                .getStatusCode().value()).isEqualTo(422);

        ResponseEntity<Map> tooMany = send(HttpMethod.PATCH, "/api/v1/me/profile", ana, Map.of("favoriteCuisines",
                List.of("ITALIANA", "JAPONESA", "MINEIRA", "BAIANA", "MEXICANA", "NORDESTINA")));
        assertThat(tooMany.getStatusCode().value()).isEqualTo(422);
        ResponseEntity<Map> unknown = send(HttpMethod.PATCH, "/api/v1/me/profile", ana,
                Map.of("favoriteCuisines", List.of("MARCIANA")));
        assertThat(unknown.getStatusCode().value()).isEqualTo(422);

        send(HttpMethod.PUT, "/api/v1/me/consents/SENSITIVE_PROFILE_DATA", ana, Map.of("granted", true));
        assertThat(send(HttpMethod.PATCH, "/api/v1/me/profile", ana, Map.of("dietaryStyles", List.of("CARNIVORO")))
                .getStatusCode().value()).isEqualTo(422);
        assertThat(users.findById(ana.getId()).orElseThrow().getFavoriteCuisines()).isNullOrEmpty();
    }

    private UserAccount active(String name, String email, LocalDate birth) {
        UserAccount user = new UserAccount(name, email, passwords.encode(PASSWORD), UserCategoryEnum.COMENSAL);
        user.setUserDateOfBirth(birth);
        user.activate();
        return user;
    }

    private UserAccount save(UserAccount user) {
        return tx.execute(s -> users.saveAndFlush(user));
    }

    private ResponseEntity<Map> send(HttpMethod method, String path, UserAccount who, Object body) {
        ApiClient browser = who == null ? new ApiClient(port) : browsers.computeIfAbsent(who.getId(), id -> {
            ApiClient b = new ApiClient(port);
            assertThat(b.login(who.getEmail(), PASSWORD).getStatusCode().value()).isEqualTo(200);
            return b;
        });
        return browser.send(method, path, body);
    }

    private static List<String> locked(Map<String, Map<String, Object>> byPurpose) {
        return byPurpose.entrySet().stream().filter(e -> (Boolean) e.getValue().get("lockedForMinor"))
                .map(Map.Entry::getKey).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> byPurpose(Map<String, Object> body) {
        return ((List<Map<String, Object>>) body.get("items")).stream()
                .collect(Collectors.toMap(c -> (String) c.get("purpose"), c -> c));
    }
}
