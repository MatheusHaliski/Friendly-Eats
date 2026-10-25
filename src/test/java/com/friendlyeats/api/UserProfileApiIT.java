package com.friendlyeats.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Perfil público e feed de perfis (RF15) ponta a ponta: só dados públicos, posts do autor com cursor, perfil privado
 * fechado, menor protegido fora da vitrine e organizações sem selo fora do feed de perfis.
 */
class UserProfileApiIT extends PostItBase {

    @Test
    void profileShowsOnlyPublicDataAndTheAuthorsPostsPageByCursor() {
        jdbc.update("UPDATE user_account SET handle = 'gula.critica', bio = 'Comida de rua e boteco' WHERE id = ?",
                creator.getId());
        publishId(creator, post("PERGUNTA", "Primeiro"));
        String archived = publishId(creator, post("PERGUNTA", "Arquivado"));
        send(HttpMethod.POST, "/api/v1/posts/" + archived + "/archive", creator, null);
        publishId(creator, post("PERGUNTA", "Cerveja de trigo?", "foodFormat", "CERVEJA"));

        Map<String, Object> profile = get("/api/v1/users/" + creator.getId() + "/profile", null).getBody();
        assertThat(profile).containsEntry("name", "Gula Crítica").containsEntry("handle", "gula.critica")
                .containsEntry("bio", "Comida de rua e boteco").containsEntry("privateProfile", false)
                .containsEntry("postCount", 3).containsEntry("verified", false)
                .containsEntry("postsPath", "/api/v1/users/" + creator.getId() + "/posts")
                .containsEntry("shareUrl", "http://localhost:3000/u/" + creator.getId())
                .doesNotContainKeys("email", "cellphoneNumber", "dateOfBirth", "cityOfResidence");
        assertThat(map(profile, "category")).containsEntry("code", "CRIADOR").containsEntry("glyph", "✦")
                .containsEntry("lucideIcon", "camera").containsEntry("color", "#F7C638")
                .containsEntry("lightColor", "#876A06");

        // o visitante não vê o post 18+; quem tem 18+ vê, com o arquivado também
        assertThat(titles(get("/api/v1/users/" + creator.getId() + "/posts", null).getBody()))
                .containsExactly("Arquivado", "Primeiro");
        Map<String, Object> page1 = get("/api/v1/users/" + creator.getId() + "/posts?size=2", ana).getBody();
        assertThat(titles(page1)).containsExactly("Cerveja de trigo?", "Arquivado");
        Map<String, Object> page2 = get("/api/v1/users/" + creator.getId() + "/posts?size=2&cursor="
                + page1.get("nextCursor"), ana).getBody();
        assertThat(titles(page2)).containsExactly("Primeiro");
        assertThat(page2.get("nextCursor")).isNull();
        assertThat(status(get("/api/v1/users/" + creator.getId() + "/posts?cursor=lixo", null))).isEqualTo(400);

        // conta suspensa ou inexistente: 404 sem dizer o motivo
        jdbc.update("UPDATE user_account SET account_status = 'SUSPENDED' WHERE id = ?", creator.getId());
        assertThat(status(get("/api/v1/users/" + creator.getId() + "/profile", null))).isEqualTo(404);
        assertThat(status(get("/api/v1/users/999999/profile", null))).isEqualTo(404);
    }

    @Test
    void privateProfileShowsTheMinimumAndClosesThePostsExceptForTheHolder() {
        publishId(teen, post("PERGUNTA", "Brigadeiro de colher?"));
        Map<String, Object> outside = get("/api/v1/users/" + teen.getId() + "/profile", ana).getBody();
        assertThat(outside).containsEntry("privateProfile", true).containsEntry("name", "Duda")
                .containsEntry("postCount", null).containsEntry("avatarUrl", null).containsEntry("bio", null);
        assertThat(status(get("/api/v1/users/" + teen.getId() + "/posts", ana))).isEqualTo(403);
        assertThat(status(get("/api/v1/users/" + teen.getId() + "/posts", null))).isEqualTo(403);

        Map<String, Object> self = get("/api/v1/users/" + teen.getId() + "/profile", teen).getBody();
        assertThat(self).containsEntry("privateProfile", true).containsEntry("postCount", 1);
        assertThat(titles(get("/api/v1/users/" + teen.getId() + "/posts", teen).getBody()))
                .containsExactly("Brigadeiro de colher?");
    }

    @Test
    void profileFeedListsActivePublicAccountsWithBadgeAndSealAndFilters() {
        jdbc.update("UPDATE user_account SET handle = 'rita.duarte', type_details = '{\"city\": \"São Paulo\"}' "
                + "WHERE id = ?", chef.getId());
        jdbc.update("UPDATE user_account SET type_details = '{\"city\": \"Sao Paulo\"}' WHERE id = ?", ana.getId());

        Map<String, Object> all = get("/api/v1/users", null).getBody();
        List<Object> names = list(all, "items").stream().map(i -> i.get("name")).toList();
        // menor de 16 (privado) e organização sem selo ficam fora; os demais, dos mais novos para os mais antigos
        assertThat(names).containsExactly("Vigilância de Teste", "Empório Bom", "Sabor Mineiro", "Confraria do Pão",
                "Rita Duarte", "Gula Crítica", "Léo", "Ana Ribeiro");
        Map<String, Object> vigilancia = list(all, "items").getFirst();
        assertThat(vigilancia).containsEntry("verified", true).containsEntry("city", "São Paulo")
                .containsEntry("profilePath", "/api/v1/users/" + official.getId() + "/profile");
        assertThat(map(vigilancia, "category")).containsEntry("glyph", "◎").containsEntry("lucideIcon", "landmark");
        // a cidade do comensal não é pública: não sai no card nem entra no filtro
        assertThat(list(all, "items").getLast()).containsEntry("city", null);

        assertThat(list(get("/api/v1/users?type=CHEF", null).getBody(), "items")).extracting(i -> i.get("name"))
                .containsExactly("Rita Duarte");
        assertThat(list(get("/api/v1/users?city=sao paulo", null).getBody(), "items")).extracting(i -> i.get("name"))
                .containsExactly("Vigilância de Teste", "Rita Duarte");
        assertThat(list(get("/api/v1/users?q=RITA", null).getBody(), "items")).extracting(i -> i.get("name"))
                .containsExactly("Rita Duarte");
        assertThat(list(get("/api/v1/users?q=%", null).getBody(), "items")).isEmpty();
        assertThat(status(get("/api/v1/users?type=ROBO", null))).isEqualTo(400);

        Map<String, Object> page1 = get("/api/v1/users?size=3", null).getBody();
        assertThat(list(page1, "items")).hasSize(3);
        Map<String, Object> page2 = get("/api/v1/users?size=3&cursor=" + page1.get("nextCursor"), null).getBody();
        assertThat(list(page2, "items")).extracting(i -> i.get("name"))
                .containsExactly("Confraria do Pão", "Rita Duarte", "Gula Crítica");
        assertThat(status(get("/api/v1/users?cursor=lixo", null))).isEqualTo(400);
    }
}
