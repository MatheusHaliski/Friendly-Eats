package com.friendlyeats.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** RF19 · bloquear, silenciar e palavras filtradas, e onde cada um vale. */
class SafetyApiIT extends PostItBase {

    @Test
    void blockWorksBothWaysOnFeedPostCommentsAndProfiles() {
        String id = publishId(creator, post("PERGUNTA", "Qual o melhor pastel da feira?"));
        String path = "/api/v1/posts/" + id;
        send(HttpMethod.POST, path + "/comments", chef, Map.of("body", "O da barraca azul."));

        assertThat(send(HttpMethod.PUT, "/api/v1/me/blocks/" + chef.getId(), creator, null).getBody())
                .containsEntry("blocked", true).containsEntry("muted", false);
        // idempotente
        assertThat(send(HttpMethod.PUT, "/api/v1/me/blocks/" + chef.getId(), creator, null).getStatusCode().value())
                .isEqualTo(200);
        assertThat(status(send(HttpMethod.PUT, "/api/v1/me/blocks/" + creator.getId(), creator, null))).isEqualTo(422);
        assertThat(status(send(HttpMethod.PUT, "/api/v1/me/blocks/999999", creator, null))).isEqualTo(404);

        // quem foi bloqueado não vê o post, o perfil nem os posts, e não comenta (o mesmo 404 de algo que não existe)
        assertThat(status(get(path, chef))).isEqualTo(404);
        assertThat(status(send(HttpMethod.POST, path + "/comments", chef, Map.of("body", "Oi?")))).isEqualTo(404);
        assertThat(status(get("/api/v1/users/" + creator.getId() + "/profile", chef))).isEqualTo(404);
        assertThat(status(get("/api/v1/users/" + creator.getId() + "/posts", chef))).isEqualTo(404);
        assertThat(titles(get("/api/v1/posts", chef).getBody())).doesNotContain("Qual o melhor pastel da feira?");
        assertThat(list(get("/api/v1/users", chef).getBody(), "items")).extracting(c -> c.get("id"))
                .doesNotContain(creator.getId().intValue());
        // e quem bloqueou deixa de ver o outro
        assertThat(status(get("/api/v1/users/" + chef.getId() + "/profile", creator))).isEqualTo(404);
        assertThat(list(get(path + "/comments", creator).getBody(), "items")).isEmpty();
        // os outros seguem vendo tudo
        assertThat(list(get(path + "/comments", ana).getBody(), "items")).hasSize(1);
        assertThat(status(get(path, null))).isEqualTo(200);

        Map<String, Object> mine = get("/api/v1/me/safety", creator).getBody();
        assertThat(list(mine, "blocked")).extracting(b -> map(b, "user").get("name")).containsExactly("Rita Duarte");

        assertThat(send(HttpMethod.DELETE, "/api/v1/me/blocks/" + chef.getId(), creator, null).getBody())
                .containsEntry("blocked", false);
        assertThat(status(get(path, chef))).isEqualTo(200);
    }

    @Test
    void muteHidesOnlyForWhoMutedAndIsSilent() {
        String id = publishId(chef, post("PERGUNTA", "Fermento natural ou biológico?"));
        send(HttpMethod.POST, "/api/v1/posts/" + id + "/comments", creator, Map.of("body", "Natural, sempre."));
        assertThat(send(HttpMethod.PUT, "/api/v1/me/mutes/" + chef.getId(), ana, null).getBody())
                .containsEntry("muted", true);
        assertThat(titles(get("/api/v1/posts", ana).getBody())).doesNotContain("Fermento natural ou biológico?");
        assertThat(get("/api/v1/me/relations/" + chef.getId(), ana).getBody()).containsEntry("muted", true);
        // o silenciado não sabe: continua vendo e interagindo com quem o silenciou
        assertThat(status(get("/api/v1/users/" + ana.getId() + "/profile", chef))).isEqualTo(200);
        assertThat(get("/api/v1/me/relations/" + ana.getId(), chef).getBody()).containsEntry("muted", false);
        // quem silenciou ainda abre o perfil se quiser
        assertThat(status(get("/api/v1/users/" + chef.getId() + "/profile", ana))).isEqualTo(200);
        send(HttpMethod.DELETE, "/api/v1/me/mutes/" + chef.getId(), ana, null);
        assertThat(titles(get("/api/v1/posts", ana).getBody())).contains("Fermento natural ou biológico?");
    }

    @Test
    void filteredWordsHideCommentsOnMyPostsForEveryoneButTheirAuthor() {
        String id = publishId(creator, post("PERGUNTA", "Melhor coxinha de BH?"));
        String box = "/api/v1/posts/" + id + "/comments";
        send(HttpMethod.POST, box, chef, Map.of("body", "Que pergunta de 1D10TAAAA"));
        send(HttpMethod.POST, box, ana, Map.of("body", "A da lixeira não, a da esquina!"));

        assertThat(status(send(HttpMethod.PUT, "/api/v1/me/filtered-words", creator,
                Map.of("words", List.of("x"))))).isEqualTo(422);
        Map<String, Object> saved = send(HttpMethod.PUT, "/api/v1/me/filtered-words", creator,
                Map.of("words", List.of("Idiota", "idiota", "lixo"))).getBody();
        assertThat((List<Object>) saved.get("words")).containsExactly("idiota", "lixo");

        // "lixo" não pega "lixeira"; l33t e letras repetidas pegam
        assertThat(list(get(box, null).getBody(), "items")).extracting(c -> c.get("body"))
                .containsExactly("A da lixeira não, a da esquina!");
        assertThat(list(get(box, creator).getBody(), "items")).hasSize(1);
        assertThat(list(get(box, chef).getBody(), "items")).hasSize(2);   // o autor do comentário vê o seu
    }

    @Test
    void defaultFilterIsOnAndLockedForMinorsAndOptionalForAdults() {
        Map<String, Object> teens = get("/api/v1/me/safety", teen).getBody();
        assertThat(map(teens, "defaultFilter")).containsEntry("enabled", true).containsEntry("locked", true);
        assertThat(status(send(HttpMethod.PATCH, "/api/v1/me/safety", teen, Map.of("defaultFilter", false))))
                .isEqualTo(403);

        assertThat(map(get("/api/v1/me/safety", ana).getBody(), "defaultFilter")).containsEntry("enabled", false);
        assertThat(map(send(HttpMethod.PATCH, "/api/v1/me/safety", ana, Map.of("defaultFilter", true)).getBody(),
                "defaultFilter")).containsEntry("enabled", true);
        String id = publishId(ana, post("PERGUNTA", "Onde comer tapioca?"));
        send(HttpMethod.POST, "/api/v1/posts/" + id + "/comments", chef, Map.of("body", "Vai morrer de fome, burra"));
        assertThat(list(get("/api/v1/posts/" + id + "/comments", null).getBody(), "items")).isEmpty();
    }
}
