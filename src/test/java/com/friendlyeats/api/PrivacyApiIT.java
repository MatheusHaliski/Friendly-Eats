package com.friendlyeats.api;

import com.friendlyeats.application.media.MediaStorage;
import com.friendlyeats.application.privacy.AccessLogService;
import com.friendlyeats.application.privacy.AccessLogService.ConsultRequest;
import com.friendlyeats.application.privacy.DataExportService;
import com.friendlyeats.application.privacy.SecurityIncidentService;
import com.friendlyeats.application.privacy.SecurityIncidentService.IncidentDraft;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.support.CapturingNotifier;
import com.friendlyeats.support.GarageContainer;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

/**
 * Central do titular (RF18): contato do encarregado, exportação com sudo e link pré-assinado, registros de acesso
 * do Marco Civil com o IP cifrado e o inventário de consultas, e o registro de incidentes.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
class PrivacyApiIT {

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
    DataExportService exports;
    @Autowired
    MediaStorage storage;
    @Autowired
    AccessLogService accessLog;
    @Autowired
    SecurityIncidentService incidents;
    @Autowired
    JsonMapper json;
    @Autowired
    com.friendlyeats.application.account.AccountService accounts;

    ItAccounts it;

    @BeforeEach
    void seed() {
        it = new ItAccounts(port, users, jdbc, tx, passwords, redis);
    }

    @Test
    void privacyContactIsPublic() {
        Map<String, Object> contact = it.ok(GET, "/api/v1/legal/privacy-contact", null, null);
        assertThat(contact).containsEntry("dpoName", "Encarregada de Teste").containsEntry("dpoEmail", "dpo@example.com")
                .containsEntry("privacyEmail", "dpo@example.com").containsEntry("requestDeadlineDays", 15)
                .containsEntry("exportLinkHours", 72).containsEntry("controllerName", "Friendly Eats de Teste Ltda");
        assertThat((List<?>) contact.get("rights")).hasSizeGreaterThanOrEqualTo(8);
    }

    @Test
    void exportNeedsSudoOncePerDayAndProducesAZipWithTheData() throws Exception {
        UserAccount ana = it.adult("Ana Ribeiro", "ana@example.com");
        it.verifiedAdult(ana);
        it.ok(PUT, "/api/v1/me/consents/MARKETING", ana, Map.of("granted", true));
        // segurança entre usuários (RF19): o que a Ana fez entra na cópia; quem bloqueou a Ana, não
        UserAccount bia = it.adult("Bia Souza", "bia@example.com");
        UserAccount caio = it.adult("Caio Lima", "caio@example.com");
        it.ok(PUT, "/api/v1/me/blocks/" + bia.getId(), ana, null);
        it.ok(PUT, "/api/v1/me/filtered-words", ana, Map.of("words", List.of("lixo")));
        it.ok(PUT, "/api/v1/me/blocks/" + ana.getId(), caio, null);

        assertThat(it.status(POST, "/api/v1/me/data-exports", ana, Map.of("password", "errada"))).isEqualTo(403);
        Map<String, Object> requested = it.ok(POST, "/api/v1/me/data-exports", ana, Map.of("password", ItAccounts.PASSWORD));
        assertThat(requested).containsEntry("status", "REQUESTED").containsEntry("downloadUrl", null);
        Instant at = Instant.parse((String) requested.get("requestedAt"));
        assertThat(Instant.parse((String) requested.get("dueAt"))).isEqualTo(at.plus(Duration.ofDays(15)));
        assertThat(notifier.events).contains("export-requested:ana@example.com");
        assertThat(it.status(POST, "/api/v1/me/data-exports", ana, Map.of("password", ItAccounts.PASSWORD))).isEqualTo(429);

        assertThat(exports.processPending()).isEqualTo(1);
        assertThat(notifier.events).contains("export-ready:ana@example.com");
        @SuppressWarnings("unchecked")
        Map<String, Object> ready = (Map<String, Object>) it.browser(ana).getList("/api/v1/me/data-exports").getBody().getFirst();
        assertThat(ready).containsEntry("status", "READY").containsEntry("statusLabel", "Pronto para baixar");
        String url = (String) ready.get("downloadUrl");
        assertThat(url).contains("exports/").contains("X-Amz-Signature");
        // o ZIP fica no bucket privado (o mesmo dos originais em uploads/), nunca no de mídias servido pela CDN
        assertThat(url).contains("/" + GarageContainer.UPLOADS_BUCKET + "/exports/")
                .doesNotContain("/" + GarageContainer.BUCKET + "/");

        Map<String, String> files = unzip(download(url));
        assertThat(files).containsKeys("LEIA-ME.txt", "conta.json", "perfil.json", "preferencias.json",
                "consentimentos.json", "termos.json", "sessoes.json", "posts.json", "comentarios.json", "curtidas.json",
                "salvos.json", "avaliacoes.json", "estabelecimentos.json", "idade.json", "responsavel.json",
                "seguranca.json", "moderacao.json", "exportacoes.json");
        assertThat(json.readTree(files.get("perfil.json")).get("name").asString()).isEqualTo("Ana Ribeiro");
        assertThat(files.get("conta.json")).doesNotContain("password_hash").doesNotContain("argon2");
        JsonNode consents = json.readTree(files.get("consentimentos.json"));
        assertThat(consents.get("historico").toString()).contains("MARKETING").doesNotContain("ip_hash");
        assertThat(json.readTree(files.get("idade.json")).get("atual").get("status").asString()).isEqualTo("VERIFIED_ADULT");
        JsonNode safety = json.readTree(files.get("seguranca.json"));
        assertThat(safety.get("bloqueados")).hasSize(1);
        assertThat(safety.get("bloqueados").get(0).get("userId").asLong()).isEqualTo(bia.getId());
        assertThat(safety.get("palavrasFiltradas").get(0).get("word").asString()).isEqualTo("lixo");
        assertThat(files.get("seguranca.json")).doesNotContain("\"userId\" : " + caio.getId());
        assertThat(json.readTree(files.get("moderacao.json")).get("denunciasFeitas")).isEmpty();

        // 72 h depois: o link vence e o ZIP sai do bucket
        String key = jdbc.queryForObject("SELECT object_key FROM data_export", String.class);
        assertThat(storage.head(key)).isPresent();
        jdbc.update("UPDATE data_export SET expires_at = now() - interval '1 minute'");
        assertThat(exports.expire()).isEqualTo(1);
        assertThat(storage.head(key)).isEmpty();
        @SuppressWarnings("unchecked")
        Map<String, Object> expired = (Map<String, Object>) it.browser(ana).getList("/api/v1/me/data-exports").getBody().getFirst();
        assertThat(expired).containsEntry("status", "EXPIRED").containsEntry("downloadUrl", null);
    }

    @Test
    void eliminationErasesExportsAndAgeVerificationButKeepsTheAccessLog() {
        UserAccount ana = it.adult("Ana", "ana@example.com");
        it.verifiedAdult(ana);
        it.ok(POST, "/api/v1/me/data-exports", ana, Map.of("password", ItAccounts.PASSWORD));
        exports.processPending();
        String key = jdbc.queryForObject("SELECT object_key FROM data_export", String.class);
        jdbc.update("UPDATE user_account SET account_status = 'PENDING_DELETION', "
                + "deletion_scheduled_for = now() - interval '1 minute' WHERE id = ?", ana.getId());
        accounts.eliminate(ana.getId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM data_export", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM age_verification", Integer.class)).isZero();
        assertThat(storage.head(key)).isEmpty();
        // Marco Civil, art. 15: o registro de acesso fica pelos 6 meses
        assertThat(jdbc.queryForObject("SELECT count(*) FROM access_log WHERE user_id = ?", Integer.class, ana.getId()))
                .isPositive();
    }

    @Test
    void accessLogsKeepARecoverableEncryptedIpAndAnInventoryOfQueries() {
        UserAccount ana = it.adult("Ana", "ana@example.com");
        it.ok(GET, "/api/v1/me/profile", ana, null);
        // o login grava e vale como o acesso da hora: os pedidos seguintes do mesmo IP não gravam de novo
        assertThat(jdbc.queryForList("SELECT event FROM access_log WHERE user_id = ?", String.class, ana.getId()))
                .containsExactly("LOGIN");
        // passada a hora (a marca no Valkey expira), o próximo pedido grava um ACCESS
        redis.delete(redis.keys("fe:access-log:*"));
        it.ok(GET, "/api/v1/me/preferences", ana, null);
        it.ok(GET, "/api/v1/me/preferences", ana, null);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT event, ip_cipher FROM access_log WHERE user_id = ?",
                ana.getId());
        assertThat(rows).extracting(r -> r.get("event")).containsExactlyInAnyOrder("LOGIN", "ACCESS");
        assertThat(new String((byte[]) rows.getFirst().get("ip_cipher"), StandardCharsets.ISO_8859_1)).doesNotContain("127.0.0.1");

        Instant from = Instant.now().minus(Duration.ofHours(1));
        Instant to = Instant.now().plus(Duration.ofHours(1));
        assertThatThrownBy(() -> accessLog.consult(new ConsultRequest("juridico@example.com", "", null, ana.getId(),
                from, to))).hasMessageContaining("Revise");
        AccessLogService.ConsultResult result = accessLog.consult(new ConsultRequest("juridico@example.com",
                "Ordem judicial de entrega de registros", "0001234-56.2026.8.26.0100", ana.getId(), from, to));
        assertThat(result.entries()).hasSize(2).allSatisfy(e -> {
            assertThat(e.ip()).isIn("127.0.0.1", "0:0:0:0:0:0:0:1");
            assertThat(e.port()).isPositive();
        });
        assertThat(jdbc.queryForMap("SELECT requested_by, legal_order, rows_returned FROM access_log_query WHERE id = ?",
                result.queryId())).containsEntry("legal_order", "0001234-56.2026.8.26.0100").containsEntry("rows_returned", 2);

        // 6 meses: o antigo sai; com guarda estendida (art. 15, §2º), fica
        jdbc.update("UPDATE access_log SET occurred_at = now() - interval '200 days' WHERE event = 'LOGIN'");
        accessLog.hold(ana.getId(), Instant.now().plus(Duration.ofDays(30)));
        assertThat(accessLog.purgeExpired()).isZero();
        jdbc.update("UPDATE access_log SET legal_hold_until = NULL");
        assertThat(accessLog.purgeExpired()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT event FROM access_log", String.class)).containsExactly("ACCESS");
    }

    @Test
    void incidentsAreRecordedWithTheThreeBusinessDayClock() {
        // sexta-feira, 23/10/2026, 10h em Brasília → prazo no fim da quarta, 28/10/2026 (já vencido)
        Instant aware = Instant.parse("2026-10-23T13:00:00Z");
        var incident = incidents.register(new IncidentDraft("Vazamento de e-mails", "Bucket com logs exposto.",
                aware.minus(Duration.ofHours(2)), aware, List.of("e-mail", "IP"), 120, "Bucket fechado; chaves trocadas.",
                "seguranca@example.com"));
        assertThat(incident.communicationDeadline()).isEqualTo(Instant.parse("2026-10-29T02:59:59Z"));
        assertThat(incident.decision()).isEqualTo(SecurityIncidentService.Decision.PENDING);
        assertThat(incident.retainUntil()).isAfter(Instant.now().plus(Duration.ofDays(5 * 365)));
        assertThat(incidents.overdue()).extracting(SecurityIncidentService.Incident::id).contains(incident.id());

        var decided = incidents.decide(incident.id(), true, true, "Dados de contato de 120 titulares.");
        assertThat(decided.decision()).isEqualTo(SecurityIncidentService.Decision.NOTIFY);
        assertThat(incidents.notified(incident.id(), Instant.now(), Instant.now()).anpdNotifiedAt()).isNotNull();
        assertThat(incidents.overdue()).isEmpty();
        assertThat(incidents.purgeExpired()).isZero();
        jdbc.update("UPDATE security_incident SET retain_until = now() - interval '1 day'");
        assertThat(incidents.purgeExpired()).isEqualTo(1);
    }

    private static byte[] download(String url) throws IOException, InterruptedException {
        HttpResponse<byte[]> r = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(r.statusCode()).isEqualTo(200);
        return r.body();
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> out = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                out.put(e.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return out;
    }
}
