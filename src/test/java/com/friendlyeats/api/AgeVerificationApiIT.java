package com.friendlyeats.api;

import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

/**
 * Verificação de idade (RF17): começar no fornecedor simulado, concluir com o resultado perguntado pelo servidor,
 * histórico, data declarada que só restringe, contestação revisada por uma pessoa e a regra do 18+ que passa a
 * depender da verificação.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
class AgeVerificationApiIT {

    static final String STATUS = "/api/v1/me/age-verification";

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

    ItAccounts it;

    @BeforeEach
    void seed() {
        it = new ItAccounts(port, users, jdbc, tx, passwords, redis);
    }

    @Test
    void adultVerifiesWithTheProviderAndOnlyTheResultIsStored() {
        UserAccount ana = it.adult("Ana", "ana@example.com");
        Map<String, Object> before = it.ok(GET, STATUS, ana, null);
        assertThat(before).containsEntry("status", "UNVERIFIED").containsEntry("alcoholContentVisible", false)
                .containsEntry("declaredAdult", true).containsEntry("canStart", true)
                .containsEntry("providerAvailable", true).containsEntry("simulated", true)
                .containsEntry("canAppeal", false);

        Map<String, Object> start = it.ok(POST, STATUS + "/start", ana, null);
        String ref = (String) start.get("reference");
        assertThat((String) start.get("redirectUrl")).contains("/configuracoes/idade?verificacao=" + ref);
        assertThat(it.ok(GET, STATUS, ana, null)).containsEntry("status", "PENDING");

        // o navegador não informa o resultado: sem o retorno do fornecedor segue pendente; outra referência é 409
        assertThat(it.ok(POST, STATUS + "/complete", ana, Map.of("reference", ref, "params", Map.of())))
                .containsEntry("status", "PENDING");
        assertThat(it.status(POST, STATUS + "/complete", ana, Map.of("reference", "outra",
                "params", Map.of("simulate", "ADULT")))).isEqualTo(409);

        Map<String, Object> done = it.ok(POST, STATUS + "/complete", ana,
                Map.of("reference", ref, "params", Map.of("simulate", "ADULT")));
        assertThat(done).containsEntry("status", "VERIFIED_ADULT").containsEntry("alcoholContentVisible", true)
                .containsEntry("method", "SIMULADO").containsEntry("canStart", false);
        assertThat(done.get("verifiedAt")).isNotNull();
        assertThat(done.get("validUntil")).isNotNull();
        assertThat(history(done)).containsExactly("VERIFIED_ADULT", "PENDING");
        assertThat(it.status(POST, STATUS + "/start", ana, null)).isEqualTo(409);

        // só o resultado: nenhuma coluna de documento ou foto (Decreto 12.880, art. 24, §3º)
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns WHERE table_name = 'age_verification'
                """, String.class)).noneMatch(c -> c.contains("document") || c.contains("image") || c.contains("birth"));
    }

    @Test
    void declaredMinorCannotStartAndAnonymousNeedsLogin() {
        UserAccount teen = it.person("Bia", "bia@example.com", LocalDate.now().minusYears(17));
        assertThat(it.ok(GET, STATUS, teen, null)).containsEntry("declaredAdult", false).containsEntry("canStart", false);
        assertThat(it.status(POST, STATUS + "/start", teen, null)).isEqualTo(403);
        assertThat(it.status(GET, STATUS, null, null)).isEqualTo(401);
    }

    @Test
    void minorResultCanBeContestedAndAPersonDecides() {
        UserAccount caio = it.adult("Caio", "caio@example.com");
        UserAccount reviewer = it.adult("Revisor", "revisor@example.com");
        String ref = (String) it.ok(POST, STATUS + "/start", caio, null).get("reference");
        Map<String, Object> minor = it.ok(POST, STATUS + "/complete", caio,
                Map.of("reference", ref, "params", Map.of("simulate", "MINOR")));
        assertThat(minor).containsEntry("status", "VERIFIED_MINOR").containsEntry("alcoholContentVisible", false)
                .containsEntry("canAppeal", true);

        assertThat(it.status(POST, STATUS + "/appeal", caio, Map.of("reason", "curto"))).isEqualTo(422);
        Map<String, Object> appeal = it.ok(POST, STATUS + "/appeal", caio,
                Map.of("reason", "Tenho 36 anos; a selfie ficou escura."));
        assertThat(appeal).containsEntry("status", "OPEN").containsEntry("contested", "VERIFIED_MINOR");
        assertThat(it.status(POST, STATUS + "/appeal", caio, Map.of("reason", "De novo, com outro texto."))).isEqualTo(409);
        assertThat(it.status(POST, STATUS + "/start", caio, null)).isEqualTo(409);

        // a fila é só de quem revisa; quem revisa não decide a própria
        assertThat(it.status(GET, "/api/v1/review/age-appeals", caio, null)).isEqualTo(403);
        List<?> queue = it.browser(reviewer).getList("/api/v1/review/age-appeals").getBody();
        assertThat(queue).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) queue.getFirst();
        assertThat(item).containsEntry("handle", "caio").containsEntry("contested", "VERIFIED_MINOR")
                .doesNotContainKeys("dateOfBirth", "email");
        long id = ((Number) item.get("id")).longValue();
        assertThat(it.status(POST, "/api/v1/review/age-appeals/" + id, reviewer, Map.of())).isEqualTo(422);
        Map<String, Object> decided = it.ok(POST, "/api/v1/review/age-appeals/" + id, reviewer,
                Map.of("accept", true, "note", "Refazer com outro método."));
        assertThat(decided).containsEntry("status", "ACCEPTED").containsEntry("decisionNote", "Refazer com outro método.");
        assertThat(notifier.events).contains("age-appeal-true:caio@example.com");

        Map<String, Object> after = it.ok(GET, STATUS, caio, null);
        assertThat(after).containsEntry("status", "UNVERIFIED").containsEntry("canStart", true);
        assertThat(history(after).getFirst()).isEqualTo("UNVERIFIED");
        assertThat(it.status(POST, "/api/v1/review/age-appeals/" + id, reviewer, Map.of("accept", false))).isEqualTo(404);
    }

    @Test
    void verificationExpiresAfterTheValidity() {
        UserAccount ana = it.adult("Ana", "ana@example.com");
        it.verifiedAdult(ana);
        assertThat(it.ok(GET, STATUS, ana, null)).containsEntry("alcoholContentVisible", true);
        jdbc.update("UPDATE age_verification SET verified_at = now() - interval '400 days' WHERE user_id = ?", ana.getId());
        assertThat(it.ok(GET, STATUS, ana, null)).containsEntry("status", "UNVERIFIED")
                .containsEntry("alcoholContentVisible", false).containsEntry("canStart", true);
    }

    @SuppressWarnings("unchecked")
    private static List<String> history(Map<String, Object> view) {
        return ((List<Map<String, Object>>) view.get("history")).stream().map(h -> (String) h.get("status")).toList();
    }
}
