package com.friendlyeats.api;

import com.friendlyeats.application.guardian.GuardianService;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.support.ApiClient;
import com.friendlyeats.support.CapturingNotifier;
import com.friendlyeats.support.ItAccounts;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

/**
 * Conta de menor vinculada à do responsável (RF17; ECA Digital, arts. 17, 18 e 24): convite no cadastro, aceite só
 * por adulto verificado, pedidos do menor aprovados ou negados, proteções e consentimento sensível pelo painel,
 * histórico, desvincular e o aviso de vínculo pendente.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
class GuardianApiIT {

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
    CapturingNotifier.Captured notifier;
    @Autowired
    GuardianService guardians;

    ItAccounts it;

    @BeforeEach
    void seed() {
        it = new ItAccounts(port, users, jdbc, tx, passwords, redis);
        notifier.guardianTokens.clear();
    }

    @Test
    void guardianAcceptsSupervisesAndUnlinks() {
        ApiClient teen = register("duda@example.com", "duda", LocalDate.now().minusYears(14), "mae@example.com");
        assertThat(notifier.events).contains("guardian-invite:mae@example.com");
        String token = notifier.guardianTokens.get("mae@example.com");
        Map<String, Object> pending = teen.get("/api/v1/me/guardian").getBody();
        assertThat(pending).containsEntry("required", true).containsEntry("status", "PENDING")
                .containsEntry("guardianEmail", "mae@example.com").containsEntry("canUnlink", false)
                .containsEntry("overdue", false);
        // sem o vínculo aceito: proteções no máximo e nada de pedido
        assertThat(teen.send(PATCH, "/api/v1/me/preferences", Map.of("profilePrivate", false)).getStatusCode().value())
                .isEqualTo(403);
        assertThat(teen.post("/api/v1/me/guardian/requests", Map.of("protection", "PUBLIC_PROFILE"))
                .getStatusCode().value()).isEqualTo(409);

        // o responsável precisa ter a idade verificada
        UserAccount mom = it.adult("Marta Souza", "marta@example.com");
        ResponseEntity<Map> unverified = it.send(POST, "/api/v1/guardian/links/accept", mom, Map.of("token", token));
        assertThat(unverified.getStatusCode().value()).isEqualTo(403);
        assertThat(unverified.getBody()).containsEntry("ageVerificationRequired", true);
        it.verifiedAdult(mom);
        assertThat(it.status(POST, "/api/v1/guardian/links/accept", mom, Map.of("token", "inventado"))).isEqualTo(404);
        Map<String, Object> linked = it.ok(POST, "/api/v1/guardian/links/accept", mom, Map.of("token", token));
        assertThat(linked).containsEntry("handle", "duda").containsEntry("age", 14);
        assertThat(notifier.events).contains("guardian-linked:duda@example.com");
        assertThat(it.status(POST, "/api/v1/guardian/links/accept", mom, Map.of("token", token))).isEqualTo(404);
        Map<String, Object> active = teen.get("/api/v1/me/guardian").getBody();
        assertThat(active).containsEntry("status", "ACTIVE");
        assertThat(map(active, "guardian")).containsEntry("name", "Marta Souza");
        long minorId = ((Number) linked.get("minorId")).longValue();
        String panel = "/api/v1/guardian/minors/" + minorId;

        // pedido do menor → o responsável aprova
        ResponseEntity<Map> request = teen.post("/api/v1/me/guardian/requests", Map.of("protection", "PUBLIC_PROFILE"));
        assertThat(request.getStatusCode().value()).isEqualTo(201);
        assertThat(request.getBody()).containsEntry("status", "PENDING").containsEntry("label", "Perfil público");
        assertThat(teen.post("/api/v1/me/guardian/requests", Map.of("protection", "PUBLIC_PROFILE"))
                .getStatusCode().value()).isEqualTo(409);
        assertThat(notifier.events).contains("guardian-request:marta@example.com");
        assertThat(it.browser(mom).getList("/api/v1/guardian/minors").getBody()).hasSize(1);
        Map<String, Object> view = it.ok(GET, panel, mom, null);
        assertThat(view).containsEntry("protectionsLocked", true).containsEntry("sensitiveConsent", false)
                .containsEntry("sensitiveConsentAvailable", true);
        assertThat(map(view, "minor")).containsEntry("pendingRequests", 1);
        long requestId = ((Number) request.getBody().get("id")).longValue();
        Map<String, Object> approved = it.ok(POST, panel + "/requests/" + requestId, mom, Map.of("approve", true));
        assertThat(protection(approved, "PUBLIC_PROFILE")).isTrue();
        assertThat(teen.get("/api/v1/me/preferences").getBody()).containsEntry("profilePrivate", false);
        // repetir o valor liberado passa; religar passa; afrouxar de novo sozinho não
        assertThat(teen.send(PATCH, "/api/v1/me/preferences", Map.of("profilePrivate", false)).getStatusCode().value())
                .isEqualTo(200);
        assertThat(teen.send(PATCH, "/api/v1/me/preferences", Map.of("profilePrivate", true)).getStatusCode().value())
                .isEqualTo(200);
        assertThat(teen.send(PATCH, "/api/v1/me/preferences", Map.of("profilePrivate", false)).getStatusCode().value())
                .isEqualTo(403);

        // direto no painel: conteúdo sensível liberado
        assertThat(protection(it.ok(PUT, panel + "/protections/SENSITIVE_CONTENT", mom, Map.of("allowed", true)),
                "SENSITIVE_CONTENT")).isTrue();
        assertThat(teen.get("/api/v1/me/preferences").getBody()).containsEntry("sensitiveContent", "ALLOW");

        // dado sensível do perfil: só com o consentimento do responsável (LGPD Art. 14)
        assertThat(teen.send(PUT, "/api/v1/me/consents/SENSITIVE_PROFILE_DATA", Map.of("granted", true))
                .getStatusCode().value()).isEqualTo(403);
        assertThat(it.ok(PUT, panel + "/sensitive-consent", mom, Map.of("granted", true)))
                .containsEntry("sensitiveConsent", true);
        assertThat(teen.send(PUT, "/api/v1/me/consents/SENSITIVE_PROFILE_DATA", Map.of("granted", true))
                .getStatusCode().value()).isEqualTo(200);
        assertThat(teen.send(PATCH, "/api/v1/me/profile", Map.of("dietaryStyles", List.of("SEM_GLUTEN")))
                .getStatusCode().value()).isEqualTo(200);
        it.ok(PUT, panel + "/sensitive-consent", mom, Map.of("granted", false));
        assertThat(teen.get("/api/v1/me/profile").getBody().get("dietaryStyles")).isEqualTo(List.of());

        // outra pessoa não vê o painel; o menor de 16 não desfaz o vínculo
        UserAccount stranger = it.adult("Outra", "outra@example.com");
        assertThat(it.status(GET, panel, stranger, null)).isEqualTo(404);
        assertThat(teen.send(DELETE, "/api/v1/me/guardian", null).getStatusCode().value()).isEqualTo(403);

        // o responsável desfaz: as proteções voltam ao máximo e o histórico guarda tudo
        assertThat(it.status(DELETE, panel, mom, null)).isEqualTo(204);
        Map<String, Object> prefs = teen.get("/api/v1/me/preferences").getBody();
        assertThat(prefs).containsEntry("profilePrivate", true).containsEntry("sensitiveContent", "LIMIT");
        Map<String, Object> after = teen.get("/api/v1/me/guardian").getBody();
        assertThat(after).containsEntry("status", "NONE");
        assertThat(actions(after)).contains("INVITED", "ACCEPTED", "REQUESTED", "APPROVED", "PROTECTION_ALLOWED",
                "SENSITIVE_CONSENT_GRANTED", "SENSITIVE_CONSENT_REVOKED", "UNLINKED");
        assertThat(it.browser(mom).getList("/api/v1/guardian/minors").getBody()).isEmpty();
    }

    @Test
    void reinviteResendLimitAndPendingReminder() {
        ApiClient teen = register("leo@example.com", "leo", LocalDate.now().minusYears(13), "pai@example.com");
        assertThat(teen.post("/api/v1/me/guardian/invite", Map.of()).getStatusCode().value()).isEqualTo(429);
        jdbc.update("UPDATE guardian_link SET invited_at = now() - interval '8 days'");
        Map<String, Object> overdue = teen.get("/api/v1/me/guardian").getBody();
        assertThat(overdue).containsEntry("overdue", true);
        assertThat(((Number) overdue.get("pendingDays")).intValue()).isEqualTo(8);
        assertThat(guardians.remindPending()).isEqualTo(1);
        assertThat(guardians.remindPending()).isZero();
        assertThat(notifier.events).contains("guardian-pending:leo@example.com");

        // outro e-mail troca o convite; o antigo deixa de valer
        String oldToken = notifier.guardianTokens.get("pai@example.com");
        Map<String, Object> replaced = teen.post("/api/v1/me/guardian/invite",
                Map.of("guardianEmail", "Avo@Example.com")).getBody();
        assertThat(replaced).containsEntry("status", "PENDING").containsEntry("guardianEmail", "avo@example.com")
                .containsEntry("overdue", false);
        UserAccount dad = it.adult("Pai", "pai@example.com");
        it.verifiedAdult(dad);
        assertThat(it.status(POST, "/api/v1/guardian/links/accept", dad, Map.of("token", oldToken))).isEqualTo(404);
        assertThat(teen.post("/api/v1/me/guardian/invite", Map.of("guardianEmail", "leo@example.com"))
                .getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void sixteenToSeventeenMayLinkOptionallyAndUnlink() {
        UserAccount older = it.person("Caio", "caio@example.com", LocalDate.now().minusYears(17));
        Map<String, Object> none = it.ok(GET, "/api/v1/me/guardian", older, null);
        assertThat(none).containsEntry("required", false).containsEntry("eligible", true).containsEntry("status", "NONE");
        it.ok(POST, "/api/v1/me/guardian/invite", older, Map.of("guardianEmail", "tia@example.com"));
        UserAccount aunt = it.adult("Tia", "tia@example.com");
        it.verifiedAdult(aunt);
        it.ok(POST, "/api/v1/guardian/links/accept", aunt, Map.of("token", notifier.guardianTokens.get("tia@example.com")));
        // 16+: proteções do ECA não travam; o pedido não é necessário e o próprio adolescente pode desvincular
        assertThat(it.status(POST, "/api/v1/me/guardian/requests", older, Map.of("protection", "PUBLIC_PROFILE")))
                .isEqualTo(409);
        assertThat(it.ok(DELETE, "/api/v1/me/guardian", older, null)).containsEntry("status", "NONE");

        UserAccount adult = it.adult("Adulta", "adulta@example.com");
        assertThat(it.status(POST, "/api/v1/me/guardian/invite", adult, Map.of("guardianEmail", "x@example.com")))
                .isEqualTo(403);
    }

    private ApiClient register(String email, String handle, LocalDate birth, String guardianEmail) {
        Map<String, Object> r = new HashMap<>();
        r.put("name", "Pessoa " + handle);
        r.put("handle", handle);
        r.put("email", email);
        r.put("password", ItAccounts.PASSWORD);
        r.put("userType", "COMENSAL");
        r.put("dateOfBirth", birth.toString());
        r.put("acceptTerms", true);
        r.put("termsVersion", new ApiClient(port).get("/api/v1/legal/terms").getBody().get("version"));
        r.put("typeDetails", Map.of("guardianEmail", guardianEmail));
        ApiClient browser = new ApiClient(port);
        assertThat(browser.post("/api/v1/auth/register", r).getStatusCode().value()).isEqualTo(201);
        return browser;
    }

    @SuppressWarnings("unchecked")
    private static boolean protection(Map<String, Object> panel, String code) {
        return ((List<Map<String, Object>>) panel.get("protections")).stream()
                .filter(p -> code.equals(p.get("code"))).map(p -> (Boolean) p.get("allowed")).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static List<String> actions(Map<String, Object> view) {
        return ((List<Map<String, Object>>) view.get("history")).stream().map(a -> (String) a.get("action")).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> parent, String key) {
        return (Map<String, Object>) parent.get(key);
    }
}
