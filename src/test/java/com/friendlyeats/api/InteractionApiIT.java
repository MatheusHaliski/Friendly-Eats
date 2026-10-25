package com.friendlyeats.api;

import com.friendlyeats.application.post.PostContentEraser;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Interações (RF12) ponta a ponta: curtir e salvar idempotentes, comentários com o {@code who_can_comment} do autor e
 * as proteções de menor, compartilhar com o link público, alerta só com compartilhar e salvar, e a eliminação da conta
 * (RF5) apagando posts, comentários, curtidas e fotos.
 */
class InteractionApiIT extends PostItBase {

    @Autowired
    PostContentEraser eraser;

    @Test
    void likeAndSaveAreIdempotentCountedOnceAndNeedAnActiveLogin() {
        String id = publishId(creator, post("PERGUNTA", "Qual o melhor pastel da feira?"));
        String like = "/api/v1/posts/" + id + "/like";

        assertThat(status(send(HttpMethod.PUT, like, null, null))).isEqualTo(401);
        UserAccount pending = save(new UserAccount("Pendente", "pend@example.com", passwords.encode(PASSWORD),
                com.friendlyeats.domain.model.UserCategoryEnum.COMENSAL));
        assertThat(status(send(HttpMethod.PUT, like, pending, null))).isEqualTo(403);

        assertThat(send(HttpMethod.PUT, like, ana, null).getBody()).containsEntry("liked", true).containsEntry("likes", 1);
        assertThat(send(HttpMethod.PUT, like, ana, null).getBody()).containsEntry("likes", 1);
        assertThat(send(HttpMethod.PUT, like, chef, null).getBody()).containsEntry("likes", 2);
        Map<String, Object> detail = get("/api/v1/posts/" + id, ana).getBody();
        assertThat(map(detail, "viewer")).containsEntry("liked", true).containsEntry("saved", false);
        assertThat(map(detail, "counts")).containsEntry("likes", 2);
        assertThat(map(list(get("/api/v1/posts", ana).getBody(), "items").getFirst(), "viewer"))
                .containsEntry("liked", true);

        assertThat(send(HttpMethod.DELETE, like, ana, null).getBody()).containsEntry("liked", false).containsEntry("likes", 1);
        assertThat(send(HttpMethod.DELETE, like, ana, null).getBody()).containsEntry("likes", 1);

        // salvar é privado: sem contador, e só quem salvou vê o estado
        assertThat(send(HttpMethod.PUT, "/api/v1/posts/" + id + "/save", ana, null).getBody()).containsEntry("saved", true);
        assertThat(map(get("/api/v1/posts/" + id, ana).getBody(), "viewer")).containsEntry("saved", true);
        assertThat(map(get("/api/v1/posts/" + id, chef).getBody(), "viewer")).containsEntry("saved", false);
        assertThat(send(HttpMethod.DELETE, "/api/v1/posts/" + id + "/save", ana, null).getBody()).containsEntry("saved", false);

        // arquivado: lê, mas não recebe novas curtidas (409); descurtir continua valendo
        send(HttpMethod.POST, "/api/v1/posts/" + id + "/archive", creator, null);
        assertThat(status(send(HttpMethod.PUT, like, ana, null))).isEqualTo(409);
        assertThat(map(get("/api/v1/posts/" + id, ana).getBody(), "interactions")).containsEntry("open", false);
        assertThat(send(HttpMethod.DELETE, like, chef, null).getBody()).containsEntry("likes", 0);
    }

    @Test
    void commentsFollowTheAuthorsAudienceAndMinorsAreProtected() {
        String id = publishId(chef, post("PERGUNTA", "Fermento natural ou biológico?"));
        String box = "/api/v1/posts/" + id + "/comments";

        ResponseEntity<Map> created = send(HttpMethod.POST, box, ana, Map.of("body", "  Natural, sempre!  "));
        assertThat(status(created)).isEqualTo(201);
        assertThat(created.getBody()).containsEntry("comments", 1);
        Map<String, Object> comment = map(created.getBody(), "comment");
        assertThat(comment).containsEntry("body", "Natural, sempre!").containsEntry("canDelete", true);
        assertThat(map(comment, "author")).containsEntry("name", "Ana Ribeiro");
        Number commentId = (Number) comment.get("id");
        send(HttpMethod.POST, box, chef, Map.of("body", "Obrigada!"));
        assertThat(status(send(HttpMethod.POST, box, ana, Map.of("body", "   ")))).isEqualTo(400);
        assertThat(status(send(HttpMethod.POST, box, null, Map.of("body", "oi")))).isEqualTo(401);

        // lista pública, do mais antigo para o mais novo, paginada por id
        Map<String, Object> page = get(box + "?limit=1", null).getBody();
        assertThat(page).containsEntry("total", 2);
        assertThat(list(page, "items")).extracting(i -> i.get("body")).containsExactly("Natural, sempre!");
        assertThat(list(page, "items").getFirst()).containsEntry("canDelete", false);
        Map<String, Object> next = get(box + "?after=" + page.get("nextAfter"), null).getBody();
        assertThat(list(next, "items")).extracting(i -> i.get("body")).containsExactly("Obrigada!");

        // quem escreveu ou o dono do post apaga; os outros não
        assertThat(status(send(HttpMethod.DELETE, box + "/" + commentId, creator, null))).isEqualTo(403);
        assertThat(status(send(HttpMethod.DELETE, box + "/" + commentId, chef, null))).isEqualTo(204);
        assertThat(map(get("/api/v1/posts/" + id, null).getBody(), "counts")).containsEntry("comments", 1);

        // o autor fecha os comentários: só ele comenta na própria caixa
        jdbc.update("UPDATE user_preference SET who_can_comment = 'NOBODY' WHERE user_id = ?", chef.getId());
        ResponseEntity<Map> closed = send(HttpMethod.POST, box, ana, Map.of("body", "E agora?"));
        assertThat(status(closed)).isEqualTo(403);
        assertThat((String) closed.getBody().get("detail")).contains("fechou os comentários");
        assertThat(map(get("/api/v1/posts/" + id, ana).getBody(), "interactions"))
                .containsEntry("commentAudience", "NOBODY").containsEntry("viewerCanComment", false);
        assertThat(status(send(HttpMethod.POST, box, chef, Map.of("body", "Eu ainda posso.")))).isEqualTo(201);
        jdbc.update("UPDATE user_preference SET who_can_comment = 'FOLLOWERS' WHERE user_id = ?", chef.getId());
        assertThat((String) send(HttpMethod.POST, box, ana, Map.of("body", "?")).getBody().get("detail"))
                .contains("seguir perfis ainda não está disponível");

        // menor de 16: perfil privado e comentários só de seguidores, travados; adulto não comenta no post dele
        String teens = publishId(teen, post("PERGUNTA", "Receita de brigadeiro?"));
        assertThat(status(send(HttpMethod.POST, "/api/v1/posts/" + teens + "/comments", ana, Map.of("body", "Oi"))))
                .isEqualTo(404);
        assertThat(status(send(HttpMethod.PUT, "/api/v1/posts/" + teens + "/like", ana, null))).isEqualTo(404);
        // sem linha de preferências (conta antiga), os padrões do cadastro valem pela idade
        jdbc.update("DELETE FROM user_preference WHERE user_id = ?", teen.getId());
        assertThat(status(get("/api/v1/posts/" + teens, ana))).isEqualTo(404);
        assertThat(status(get("/api/v1/posts/" + teens, teen))).isEqualTo(200);
    }

    @Test
    void shareReturnsThePublicLinkAndCountsEachAccountAndChannelOnce() {
        String id = publishId(creator, post("PERGUNTA", "Melhor coxinha de BH?"));
        String shares = "/api/v1/posts/" + id + "/shares";
        Map<String, Object> first = send(HttpMethod.POST, shares, ana, Map.of("channel", "WHATSAPP")).getBody();
        assertThat(first).containsEntry("shares", 1).containsEntry("counted", true)
                .containsEntry("shareUrl", "http://localhost:3000/p/" + id);
        assertThat(send(HttpMethod.POST, shares, ana, Map.of("channel", "WHATSAPP")).getBody())
                .containsEntry("shares", 1).containsEntry("counted", false);
        assertThat(send(HttpMethod.POST, shares, ana, Map.of("channel", "COPY_LINK")).getBody()).containsEntry("shares", 2);
        assertThat(status(send(HttpMethod.POST, shares, ana, Map.of("channel", "POMBO")))).isEqualTo(400);
    }

    @Test
    void foodAlertTakesOnlySharesAndSaves() {
        String id = publishId(official, post("ALERTA_SANITARIO", "Recolhimento de palmito", "alert",
                Map.of("product", "Palmito em conserva", "brand", "Marca Exemplo", "lots", List.of("P01"),
                        "reason", "Risco de botulismo", "sourceUrl", "https://www.gov.br/anvisa/pt-br")));
        String base = "/api/v1/posts/" + id;
        assertThat(status(send(HttpMethod.PUT, base + "/like", ana, null))).isEqualTo(409);
        assertThat(status(send(HttpMethod.POST, base + "/comments", ana, Map.of("body", "Que medo")))).isEqualTo(409);
        assertThat(send(HttpMethod.POST, base + "/shares", ana, Map.of("channel", "NATIVE")).getBody())
                .containsEntry("counted", true);
        assertThat(send(HttpMethod.PUT, base + "/save", ana, null).getBody()).containsEntry("saved", true);
    }

    @Test
    void accountEliminationRemovesPostsCommentsLikesAndPhotos() throws Exception {
        String others = publishId(chef, post("PERGUNTA", "Post da Rita"));
        String base = "/api/v1/posts/" + others;
        send(HttpMethod.PUT, base + "/like", ana, null);
        send(HttpMethod.PUT, base + "/save", ana, null);
        send(HttpMethod.POST, base + "/comments", ana, Map.of("body", "comentário pessoal"));
        send(HttpMethod.POST, base + "/shares", ana, Map.of("channel", "X"));
        String upload = uploadPhoto(ana, TestImages.png(48, 48, true), "image/png");
        String mine = publishId(ana, post("FOTO_PRATO", "Meu almoço", "mediaObjectKey", upload, "altText", "Prato feito"));
        String photoKey = jdbc.queryForObject("SELECT media_object_key FROM post WHERE id = ?::uuid", String.class, mine);
        assertThat(storage.head(photoKey)).isPresent();

        // RF5: carência vencida → a saga apaga o conteúdo e, depois do commit, as fotos do bucket
        jdbc.update("UPDATE user_account SET account_status = 'PENDING_DELETION', "
                + "deletion_scheduled_for = now() - interval '1 minute' WHERE id = ?", ana.getId());
        accounts.eliminate(ana.getId());

        Map<String, Object> counts = map(get(base, null).getBody(), "counts");
        assertThat(counts).containsEntry("likes", 0).containsEntry("comments", 0).containsEntry("shares", 1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_comment WHERE body IS NOT NULL AND author_id = ?",
                Integer.class, ana.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM post_reaction WHERE user_id = ?", Integer.class,
                ana.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT status || ':' || title FROM post WHERE id = ?::uuid", String.class, mine))
                .isEqualTo("REMOVED:Conteúdo removido");
        assertThat(jdbc.queryForObject("SELECT media_object_key FROM post WHERE id = ?::uuid", String.class, mine)).isNull();
        assertThat(storage.head(photoKey)).isEmpty();
        assertThat(status(get("/api/v1/posts/" + mine, null))).isEqualTo(404);

        // idempotente
        PostContentEraser.ErasureReport again = eraser.eraseContentOf(ana.getId());
        assertThat(again.postsRemoved() + again.commentsRemoved() + again.reactionsDeleted()).isZero();
    }
}
