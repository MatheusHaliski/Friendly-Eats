package com.friendlyeats.api;

import com.friendlyeats.application.moderation.ModerationPolicy;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.persistence.postgres.UserRoleRepository;
import com.friendlyeats.support.ApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RF13 · moderação: papel de moderador, denúncia com protocolo (com conta e pelo formulário público), fila priorizada,
 * decisão motivada com histórico imutável, recurso por outra pessoa, criança em risco, contestação do dono e
 * integridade das avaliações (sinais, alerta e congelamento).
 */
class ModerationApiIT extends PostItBase {

    @Autowired
    UserRoleRepository roles;

    @Autowired
    ModerationPolicy policy;

    UserAccount mod1;
    UserAccount mod2;

    @BeforeEach
    void moderators() {
        jdbc.execute("TRUNCATE TABLE moderation_case, establishment_alert RESTART IDENTITY CASCADE");
        mod1 = save(person("Moderação Um", "mod1@example.com", UserCategoryEnum.COMENSAL, LocalDate.of(1990, 1, 1)));
        mod2 = save(person("Moderação Dois", "mod2@example.com", UserCategoryEnum.COMENSAL, LocalDate.of(1990, 1, 1)));
        roles.grant(mod1.getId(), UserRoleRepository.MODERATOR, "teste");
        roles.grant(mod2.getId(), UserRoleRepository.MODERATOR, "teste");
    }

    @AfterEach
    void signalsOff() {
        policy.setSignalsEnabled(false);
    }

    @Test
    void moderationAreaIsOnlyForModerators() {
        assertThat(status(get("/api/v1/moderation/cases", null))).isEqualTo(401);
        assertThat(status(get("/api/v1/moderation/cases", ana))).isEqualTo(403);
        assertThat(status(get("/api/v1/moderation/cases", mod1))).isEqualTo(200);
        assertThat(get("/api/v1/me/roles", mod1).getBody()).containsEntry("moderator", true);
        assertThat(get("/api/v1/me/roles", ana).getBody()).containsEntry("moderator", false);
        // tirar o papel vale na hora, mesmo com a sessão aberta
        roles.revoke(mod1.getId(), UserRoleRepository.MODERATOR);
        assertThat(status(get("/api/v1/moderation/cases", mod1))).isEqualTo(403);
        // catálogo de motivos é público
        List<Map<String, Object>> reasons = new ApiClient(port).getList("/api/v1/reports/reasons").getBody();
        assertThat(reasons).extracting(r -> r.get("code")).contains("CRIANCA_EM_RISCO", "AVALIACAO_FALSA", "OUTRO");
    }

    @Test
    void reportDecisionAndAppealByAnotherModerator() {
        String id = publishId(creator, post("PERGUNTA", "Qual o melhor pastel da feira?"));
        String path = "/api/v1/posts/" + id;

        ResponseEntity<Map> receipt = send(HttpMethod.POST, "/api/v1/reports", ana,
                Map.of("targetType", "POST", "targetId", id, "reason", "ASSEDIO", "details", "Ataques a uma pessoa."));
        assertThat(status(receipt)).isEqualTo(201);
        assertThat((String) receipt.getBody().get("protocol")).matches("DEN-\\d{4}-\\d{6}");
        // uma denúncia aberta por pessoa e alvo
        assertThat(status(send(HttpMethod.POST, "/api/v1/reports", ana,
                Map.of("targetType", "POST", "targetId", id, "reason", "SPAM")))).isEqualTo(409);
        // o próprio conteúdo não; motivo que não vale para o tipo não; anônimo não (o canal dele é o formulário)
        assertThat(status(send(HttpMethod.POST, "/api/v1/reports", creator,
                Map.of("targetType", "POST", "targetId", id, "reason", "SPAM")))).isEqualTo(422);
        assertThat(status(send(HttpMethod.POST, "/api/v1/reports", chef,
                Map.of("targetType", "PROFILE", "targetId", creator.getId().toString(),
                        "reason", "CONFLITO_INTERESSE")))).isEqualTo(422);
        assertThat(status(send(HttpMethod.POST, "/api/v1/reports", null,
                Map.of("targetType", "POST", "targetId", id, "reason", "SPAM")))).isEqualTo(401);
        assertThat(status(send(HttpMethod.POST, "/api/v1/reports", chef,
                Map.of("targetType", "POST", "targetId", id, "reason", "SPAM")))).isEqualTo(201);

        Map<String, Object> queue = get("/api/v1/moderation/cases", mod1).getBody();
        assertThat(list(queue, "items")).hasSize(1);
        Map<String, Object> summary = list(queue, "items").getFirst();
        assertThat(summary).containsEntry("reportCount", 2);
        assertThat(map(summary, "topReason")).containsEntry("code", "ASSEDIO");
        assertThat(map(summary, "priority")).containsEntry("code", "ALTA");
        long caseId = ((Number) summary.get("id")).longValue();

        // decisão sem fundamentação nem regra: 422
        assertThat(status(send(HttpMethod.POST, "/api/v1/moderation/cases/" + caseId + "/decision", mod1,
                Map.of("action", "HIDE")))).isEqualTo(422);
        Map<String, Object> decided = send(HttpMethod.POST, "/api/v1/moderation/cases/" + caseId + "/decision", mod1,
                Map.of("action", "HIDE", "policyReason", "ASSEDIO",
                        "statement", "O post humilha uma pessoa identificada, o que fere a regra de assédio.")).getBody();
        assertThat(map(decided, "summary")).extracting(s -> map(s, "status").get("code")).isEqualTo("DECIDED");
        assertThat(decided.get("currentState")).isEqualTo("Ocultado pela moderação");
        // some para os outros e para o feed; o autor ainda abre
        assertThat(status(get(path, ana))).isEqualTo(404);
        assertThat(status(get(path, null))).isEqualTo(404);
        assertThat(titles(get("/api/v1/posts", null).getBody())).doesNotContain("Qual o melhor pastel da feira?");
        assertThat(status(get(path, creator))).isEqualTo(200);
        // decidir de novo: 409; o histórico é só de inclusão (o banco recusa UPDATE)
        assertThat(status(send(HttpMethod.POST, "/api/v1/moderation/cases/" + caseId + "/decision", mod2,
                Map.of("action", "KEEP", "statement", "Revendo a decisão anterior por engano.")))).isEqualTo(409);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> jdbc.update("UPDATE moderation_decision SET statement = 'x'"))
                .hasMessageContaining("só de inclusão");

        // o autor recebe o aviso com motivo, origem e prazo, sem saber quem denunciou
        List<Map<String, Object>> mine = browsers.get(creator.getId()).getList("/api/v1/me/moderation/decisions").getBody();
        assertThat(mine).hasSize(1);
        Map<String, Object> notice = mine.getFirst();
        assertThat(notice).containsEntry("canAppeal", true).containsKey("appealDeadline");
        assertThat(map(notice, "decision")).containsEntry("automated", false)
                .containsEntry("moderatorId", null)
                .extracting(d -> map(d, "policy").get("code")).isEqualTo("ASSEDIO");
        assertThat(notice.toString()).doesNotContain("ana@example.com", "Conta #" + ana.getId());

        // quem denunciou vê "procedente"
        List<Map<String, Object>> anas = browsers.get(ana.getId()).getList("/api/v1/me/reports").getBody();
        assertThat(anas.getFirst()).containsEntry("outcome", "PROCEDENTE").containsEntry("status", "DECIDIDA");

        // recurso do autor; quem decidiu não revê
        assertThat(status(send(HttpMethod.POST, "/api/v1/me/moderation/cases/" + caseId + "/appeal", creator,
                Map.of("reason", "curto")))).isEqualTo(422);
        assertThat(status(send(HttpMethod.POST, "/api/v1/me/moderation/cases/" + caseId + "/appeal", creator,
                Map.of("reason", "Era uma brincadeira entre amigos, a pessoa citada concordou.")))).isEqualTo(201);
        assertThat(status(send(HttpMethod.POST, "/api/v1/me/moderation/cases/" + caseId + "/appeal", creator,
                Map.of("reason", "Era uma brincadeira entre amigos, a pessoa citada concordou.")))).isEqualTo(409);
        Map<String, Object> reversal = Map.of("outcome", "REVERSED",
                "statement", "A pessoa citada confirmou que concordou; o post volta ao ar.");
        assertThat(status(send(HttpMethod.POST, "/api/v1/moderation/cases/" + caseId + "/appeal-decision", mod1,
                reversal))).isEqualTo(403);
        Map<String, Object> afterAppeal = send(HttpMethod.POST,
                "/api/v1/moderation/cases/" + caseId + "/appeal-decision", mod2, reversal).getBody();
        assertThat(map(afterAppeal, "summary")).extracting(s -> map(s, "status").get("code")).isEqualTo("FINAL");
        assertThat(list(afterAppeal, "decisions")).extracting(d -> d.get("stage")).containsExactly("FIRST", "APPEAL");
        assertThat(status(get(path, ana))).isEqualTo(200);
        // a denúncia vira improcedente para a confiabilidade de quem denunciou
        anas = browsers.get(ana.getId()).getList("/api/v1/me/reports").getBody();
        assertThat(anas.getFirst()).containsEntry("outcome", "IMPROCEDENTE");

        Map<String, Object> stats = get("/api/v1/moderation/stats", mod2).getBody();
        assertThat(list(stats, "reportsByReason")).extracting(c -> c.get("code")).contains("ASSEDIO", "SPAM");
        assertThat(list(stats, "appeals")).extracting(c -> c.get("code")).containsExactly("REVERSED");
    }

    @Test
    void childAtRiskHidesAtOnceGoesFirstAndIsFlaggedForAuthorities() {
        String id = publishId(chef, post("PERGUNTA", "Fermento natural ou biológico?"));
        String box = "/api/v1/posts/" + id + "/comments";
        long comment = ((Number) map(send(HttpMethod.POST, box, creator, Map.of("body", "Olha esta foto da criança"))
                .getBody(), "comment").get("id")).longValue();
        String other = publishId(chef, post("PERGUNTA", "Outro post"));
        send(HttpMethod.POST, "/api/v1/reports", ana, Map.of("targetType", "POST", "targetId", other, "reason", "SPAM"));

        ResponseEntity<Map> r = send(HttpMethod.POST, "/api/v1/reports", ana, Map.of("targetType", "COMMENT",
                "targetId", Long.toString(comment), "reason", "CRIANCA_EM_RISCO", "childSexualAbuse", true));
        assertThat(status(r)).isEqualTo(201);
        assertThat((String) r.getBody().get("message")).contains("saiu do ar");
        // saiu do ar na hora e o contador desceu
        assertThat(list(get(box, null).getBody(), "items")).isEmpty();
        assertThat(map(get("/api/v1/posts/" + id, null).getBody(), "counts")).containsEntry("comments", 0);

        List<Map<String, Object>> queue = list(get("/api/v1/moderation/cases", mod1).getBody(), "items");
        assertThat(queue.getFirst()).containsEntry("preventive", true).containsEntry("authorityReferral", "PENDING");
        assertThat(map(queue.getFirst(), "priority")).containsEntry("code", "CRITICA");
        long caseId = ((Number) queue.getFirst().get("id")).longValue();
        Map<String, Object> detail = get("/api/v1/moderation/cases/" + caseId, mod1).getBody();
        assertThat(list(detail, "decisions").getFirst()).containsEntry("stage", "PREVENTIVE").containsEntry("automated", true);
        assertThat(map(detail, "snapshot")).containsEntry("body", "Olha esta foto da criança");

        Map<String, Object> referred = send(HttpMethod.POST, "/api/v1/moderation/cases/" + caseId + "/authority-referral",
                mod1, Map.of("reference", "PF-CNT 2026/123")).getBody();
        assertThat(map(referred, "summary")).containsEntry("authorityReferral", "SENT");

        // "manter" devolve o comentário e o contador
        send(HttpMethod.POST, "/api/v1/moderation/cases/" + caseId + "/decision", mod1, Map.of("action", "KEEP",
                "statement", "A foto é de um prato e não mostra nenhuma criança; denúncia improcedente."));
        assertThat(list(get(box, null).getBody(), "items")).hasSize(1);
        assertThat(map(get("/api/v1/posts/" + id, null).getBody(), "counts")).containsEntry("comments", 1);
    }

    @Test
    void publicFormIsIdentifiedAndRateLimited() {
        String id = publishId(creator, post("PERGUNTA", "Melhor coxinha de BH?"));
        ApiClient anonymous = new ApiClient(port);
        Map<String, Object> body = new HashMap<>(Map.of("targetType", "POST", "targetId", id, "reason", "GOLPE_FRAUDE"));
        ResponseEntity<Map> missing = anonymous.post("/api/v1/reports/public", body);
        assertThat(status(missing)).isEqualTo(422);
        assertThat(errors(missing)).contains("não pode ser anônima", "e-mail válido");
        body.put("name", "Maria Visitante");
        body.put("email", "maria@example.com");
        ResponseEntity<Map> ok = anonymous.post("/api/v1/reports/public", body);
        assertThat(status(ok)).isEqualTo(201);
        Map<String, Object> detail = get("/api/v1/moderation/cases/" + caseOf(mod1), mod1).getBody();
        assertThat(list(detail, "reports").getFirst().get("reporter").toString()).contains("maria@example.com");
        for (int i = 0; i < 4; i++) {
            anonymous.post("/api/v1/reports/public", body);
        }
        ResponseEntity<Map> limited = anonymous.post("/api/v1/reports/public", body);
        assertThat(status(limited)).isEqualTo(429);
        assertThat(limited.getHeaders().getFirst("Retry-After")).isNotBlank();
    }

    @Test
    void ownerContestsAReviewOnlyForPolicyReasonsAndTheReviewStays() {
        String reviews = "/api/v1/establishments/" + mineiro + "/reviews";
        long reviewId = ((Number) send(HttpMethod.PUT, reviews + "/me", ana,
                Map.of("rating", 1, "visitMode", "DINE_IN", "body", "Comida fria.")).getBody().get("id")).longValue();
        String contest = reviews + "/" + reviewId + "/contest";
        assertThat(status(send(HttpMethod.POST, contest, chef,
                Map.of("reason", "AVALIACAO_FALSA", "details", "Não conheço essa pessoa, nunca veio aqui.")))).isEqualTo(403);
        assertThat(status(send(HttpMethod.POST, contest, restaurant,
                Map.of("reason", "SPAM", "details", "Discordo da nota que ela deu, a comida estava ótima.")))).isEqualTo(422);
        ResponseEntity<Map> ok = send(HttpMethod.POST, contest, restaurant,
                Map.of("reason", "AVALIACAO_FALSA", "details", "Nesse dia estávamos fechados para reforma."));
        assertThat(status(ok)).isEqualTo(201);
        // a avaliação fica no ar até a decisão
        assertThat(get(reviews, null).getBody()).containsEntry("total", 1);
        Map<String, Object> detail = get("/api/v1/moderation/cases/" + caseOf(mod1), mod1).getBody();
        assertThat(map(list(detail, "reports").getFirst(), "source")).containsEntry("code", "OWNER_CONTEST");
        assertThat(detail).containsEntry("establishmentId", (int) mineiro);
    }

    @Test
    void burstOfNewAccountsGoesUnderReviewAndFreezesNewReviews() {
        policy.setSignalsEnabled(true);
        String reviews = "/api/v1/establishments/" + mineiro + "/reviews";
        List<UserAccount> crowd = List.of(ana, creator, chef, community, minor17);
        for (UserAccount who : crowd) {
            assertThat(status(send(HttpMethod.PUT, reviews + "/me", who, Map.of("rating", 5, "visitMode", "DINE_IN"))))
                    .isIn(200, 201);
        }
        // todas fora da lista e da nota; o autor vê a própria "em análise"
        assertThat(get(reviews, null).getBody()).containsEntry("total", 0);
        assertThat(get(reviews + "/me", ana).getBody()).containsEntry("status", "UNDER_REVIEW");
        // congelado: avaliação nova dá 409, editar a própria continua
        UserAccount late = save(person("Atrasada", "late@example.com", UserCategoryEnum.COMENSAL, LocalDate.of(1990, 2, 2)));
        ResponseEntity<Map> frozen = send(HttpMethod.PUT, reviews + "/me", late, Map.of("rating", 5, "visitMode", "DINE_IN"));
        assertThat(status(frozen)).isEqualTo(409);
        assertThat((String) frozen.getBody().get("detail")).contains("pausadas até");
        assertThat(status(send(HttpMethod.PUT, reviews + "/me", ana, Map.of("rating", 4, "visitMode", "DINE_IN"))))
                .isEqualTo(200);

        Map<String, Object> integrity = get("/api/v1/establishments/" + mineiro + "/integrity", null).getBody();
        assertThat(integrity).containsEntry("frozen", true);
        assertThat(list(integrity, "active")).extracting(a -> map(a, "kind").get("code")).containsExactly("ATIVIDADE_SUSPEITA");

        List<Map<String, Object>> queue = list(get("/api/v1/moderation/cases?size=50", mod1).getBody(), "items");
        assertThat(queue).extracting(c -> map(c, "target").get("type")).contains("REVIEW", "ESTABLISHMENT");
        Map<String, Object> reviewCase = queue.stream().filter(c -> map(c, "target").get("type").equals("REVIEW"))
                .findFirst().orElseThrow();
        Map<String, Object> detail = get("/api/v1/moderation/cases/" + reviewCase.get("id"), mod1).getBody();
        assertThat(list(detail, "signals")).extracting(s -> s.get("code")).contains("BURST_NEW_ACCOUNTS");
        assertThat(list(detail, "reports").getFirst()).extracting(r -> map(r, "source").get("code")).isEqualTo("SYSTEM");

        // moderação tira o congelamento e mantém uma avaliação
        long alertId = ((Number) list(integrity, "active").getFirst().get("id")).longValue();
        assertThat(status(send(HttpMethod.POST, "/api/v1/moderation/establishments/" + mineiro + "/alerts/" + alertId
                + "/lift", mod1, null))).isEqualTo(200);
        assertThat(get("/api/v1/establishments/" + mineiro + "/integrity", null).getBody()).containsEntry("frozen", false);
        send(HttpMethod.POST, "/api/v1/moderation/cases/" + reviewCase.get("id") + "/decision", mod1,
                Map.of("action", "KEEP", "statement", "Cliente real com nota fiscal enviada ao suporte."));
        assertThat(get(reviews, null).getBody()).containsEntry("total", 1);
    }

    @Test
    void declaredCourtesyLowersTheWeightAndIsShown() {
        String reviews = "/api/v1/establishments/" + mineiro + "/reviews";
        Map<String, Object> saved = send(HttpMethod.PUT, reviews + "/me", ana,
                Map.of("rating", 5, "visitMode", "DINE_IN", "disclosure", "COURTESY")).getBody();
        assertThat(saved).containsEntry("trust", "REVIEW_INCENTIVIZED").containsEntry("status", "PUBLISHED");
        assertThat(status(send(HttpMethod.PUT, reviews + "/me", ana,
                Map.of("rating", 5, "visitMode", "DINE_IN", "disclosure", "TALVEZ")))).isEqualTo(422);
    }

    private long caseOf(UserAccount moderator) {
        return ((Number) list(get("/api/v1/moderation/cases", moderator).getBody(), "items").getFirst().get("id"))
                .longValue();
    }
}
