package com.friendlyeats.api;

import com.friendlyeats.support.ApiClient;
import com.friendlyeats.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Publicar conteúdo (RF9) e o feed com a página do post (RF14) ponta a ponta, com login real e o Garage: foto sem EXIF,
 * quem publica cada tipo, edição, arquivo e exclusão, feed com cursor, encerrados fora do feed, 18+, posts do lugar,
 * "em alta" e a moldura do RF16.
 */
class PostApiIT extends PostItBase {

    @Test
    void dishPhotoGoesStraightToTheBucketWithoutExifAndTheOldPhotoLeavesIt() throws Exception {
        assertThat(status(publish(ana, post("FOTO_PRATO", "Sem foto")))).isEqualTo(422);

        String upload = uploadPhoto(ana, TestImages.jpegWithExif(400, 200, 6), "image/jpeg");
        // a chave é de Ana: outra conta não publica com ela (a mesma resposta de "não existe")
        ResponseEntity<Map> stolen = publish(creator, post("FOTO_PRATO", "Peguei", "mediaObjectKey", upload,
                "altText", "Foto"));
        assertThat(status(stolen)).isEqualTo(422);
        assertThat(errors(stolen)).contains("Envie a foto de novo");
        assertThat(errors(publish(ana, post("FOTO_PRATO", "Sem descrição", "mediaObjectKey", upload)))).contains("altText");

        ResponseEntity<Map> created = publish(ana, post("FOTO_PRATO", "  Pão de queijo  ", "mediaObjectKey", upload,
                "altText", "Seis pães de queijo numa cesta", "establishmentId", mineiro, "cuisine", "MINEIRA",
                "foodFormat", "SALGADOS", "dietaryStyles", List.of("SEM_GLUTEN"),
                "allergens", Map.of("LEITE", "CONTEM", "OVOS", "PODE_CONTER"), "warnings", List.of("LACTOSE"),
                "priceCents", 800));
        assertThat(status(created)).isEqualTo(201);
        Map<String, Object> p = created.getBody();
        String id = (String) p.get("id");
        assertThat(p).containsEntry("title", "Pão de queijo").containsEntry("status", "PUBLISHED")
                .containsEntry("mediaType", "IMAGE").containsEntry("feed", "POST").containsEntry("ageRating", "LIVRE")
                .containsEntry("allergenNotice", com.friendlyeats.domain.taxonomy.Allergen.NOTICE)
                .containsEntry("shareUrl", "http://localhost:3000/p/" + id).containsEntry("background", null);
        assertThat(map(p, "kind")).containsEntry("code", "FOTO_PRATO").containsEntry("lucideIcon", "clapperboard")
                .containsEntry("border", "SEM_MOLDURA");
        assertThat(map(p, "establishment")).containsEntry("name", "Sabor Mineiro").containsEntry("adultsOnly", false);
        assertThat(map(p, "cuisine")).containsEntry("code", "MINEIRA").containsEntry("group", "BRASIL");
        assertThat(list(p, "allergens")).extracting(a -> a.get("code") + ":" + a.get("level"))
                .containsExactly("LEITE:CONTEM", "OVOS:PODE_CONTER");
        assertThat(list(p, "warnings")).extracting(w -> w.get("label")).containsExactly("Contém lactose");
        assertThat(map(p, "price")).containsEntry("label", "R$ 8,00");
        assertThat(map(map(p, "author"), "category")).containsEntry("glyph", "◇").containsEntry("code", "COMENSAL");

        String publicKey = jdbc.queryForObject("SELECT media_object_key FROM post WHERE id = ?::uuid", String.class, id);
        assertThat(publicKey).startsWith("posts/").endsWith(".jpg");
        assertThat((String) p.get("mediaUrl")).endsWith("/" + publicKey);
        assertThat(storage.head(upload)).isEmpty();
        byte[] published = storage.read(publicKey);
        assertThat(new String(published, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("GPS");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(published));
        assertThat(img.getWidth()).isEqualTo(200);      // Orientation 6 aplicada: a foto fica "em pé"
        assertThat(img.getHeight()).isEqualTo(400);

        // trocar a foto no PATCH: a nova vai para posts/ e a anterior sai do bucket depois do commit
        String second = uploadPhoto(ana, TestImages.png(64, 64, true), "image/png");
        Map<String, Object> edited = send(HttpMethod.PATCH, "/api/v1/posts/" + id, ana,
                Map.of("mediaObjectKey", second, "altText", "Pão de queijo de perto")).getBody();
        assertThat(edited).containsEntry("altText", "Pão de queijo de perto");
        String newKey = jdbc.queryForObject("SELECT media_object_key FROM post WHERE id = ?::uuid", String.class, id);
        assertThat(newKey).startsWith("posts/").endsWith(".png").isNotEqualTo(publicKey);
        assertThat(storage.head(publicKey)).isEmpty();
        Map<String, Object> noPhoto = new HashMap<>();
        noPhoto.put("mediaObjectKey", null);
        assertThat(errors(send(HttpMethod.PATCH, "/api/v1/posts/" + id, ana, noPhoto))).contains("precisa da foto");

        // apagar o post apaga a foto do bucket
        assertThat(status(send(HttpMethod.DELETE, "/api/v1/posts/" + id, ana, null))).isEqualTo(204);
        assertThat(storage.head(newKey)).isEmpty();
        assertThat(status(get("/api/v1/posts/" + id, null))).isEqualTo(404);
    }

    @Test
    void eachKindHasItsPublishersAndTheOwnersPostsAreAboutTheirOwnPlace() {
        Instant now = Instant.now();
        String starts = now.toString();
        String ends = now.plus(Duration.ofDays(3)).toString();

        assertThat(status(publish(null, post("PERGUNTA", "Oi")))).isEqualTo(401);
        ResponseEntity<Map> seal = publish(pendingOrg, post("RECEITA", "Bolo"));
        assertThat(status(seal)).isEqualTo(403);
        assertThat((String) seal.getBody().get("detail")).contains("em verificação");
        assertThat(errors(publish(ana, post("NOVIDADE", "?")))).contains("Tipo de post desconhecido");

        // pergunta: qualquer conta, menos a oficial
        assertThat(status(publish(ana, post("PERGUNTA", "Onde tem acarajé?", "city", "São Paulo")))).isEqualTo(201);
        assertThat(status(publish(official, post("PERGUNTA", "Alguém sabe?")))).isEqualTo(403);

        // prato do dia: só o estabelecimento, sobre um lugar dele, com preço; expira às 23h59 de São Paulo
        assertThat(status(publish(ana, post("PRATO_DO_DIA", "Feijoada", "establishmentId", mineiro, "priceCents", 3990))))
                .isEqualTo(403);
        assertThat(errors(publish(restaurant, post("PRATO_DO_DIA", "Feijoada", "priceCents", 3990))))
                .contains("establishmentId");
        assertThat(status(publish(restaurant, post("PRATO_DO_DIA", "Do vizinho", "establishmentId", emporio,
                "priceCents", 3990)))).isEqualTo(403);
        Map<String, Object> daily = publish(restaurant, post("PRATO_DO_DIA", "Feijoada completa",
                "establishmentId", mineiro, "priceCents", 3990)).getBody();
        Instant expected = now.atZone(java.time.ZoneId.of("America/Sao_Paulo")).toLocalDate().atTime(23, 59, 59)
                .atZone(java.time.ZoneId.of("America/Sao_Paulo")).toInstant();
        assertThat(Instant.parse((String) daily.get("expiresAt"))).isEqualTo(expected);
        assertThat(map(daily, "kind")).containsEntry("border", "DUPLA");
        assertThat(map(daily, "price")).containsEntry("label", "R$ 39,90");

        // promoção: dono (estabelecimento ou varejo) sobre o seu lugar; criador só com o chip Publicidade
        assertThat(status(publish(chef, post("PROMOCAO", "Desconto", "startsAt", starts, "endsAt", ends,
                "discountPercent", 10)))).isEqualTo(403);
        Map<String, Object> promo = publish(market, post("PROMOCAO", "Queijos com desconto", "establishmentId", emporio,
                "startsAt", starts, "endsAt", ends, "priceFromCents", 5000, "priceToCents", 3500)).getBody();
        assertThat(map(promo, "price")).containsEntry("label", "De R$ 50,00 por R$ 35,00");
        assertThat(promo).containsEntry("advertising", false);
        assertThat(Instant.parse((String) promo.get("expiresAt"))).isEqualTo(Instant.parse(ends));
        ResponseEntity<Map> noChip = publish(creator, post("PROMOCAO", "Cupom", "startsAt", starts, "endsAt", ends,
                "discountPercent", 15));
        assertThat(errors(noChip)).contains("chip Publicidade");
        Map<String, Object> publi = publish(creator, post("PROMOCAO", "Cupom", "startsAt", starts, "endsAt", ends,
                "discountPercent", 15, "advertising", true, "establishmentId", mineiro)).getBody();
        assertThat(publi).containsEntry("advertising", true).containsEntry("advertisingLabel", "Publicidade");
        assertThat(map(publi, "price")).containsEntry("label", "15% de desconto");

        // evento: estabelecimento, varejo, comunidade ou chef; preço ou gratuito
        assertThat(status(publish(ana, post("EVENTO", "Festa", "startsAt", starts, "endsAt", ends, "free", true))))
                .isEqualTo(403);
        assertThat(errors(publish(community, post("EVENTO", "Feira do pão", "startsAt", starts, "endsAt", ends))))
                .contains("\"free\": true");
        Map<String, Object> event = publish(community, post("EVENTO", "Feira do pão", "startsAt", starts,
                "endsAt", ends, "free", true)).getBody();
        assertThat(map(event, "price")).containsEntry("label", "Gratuito").containsEntry("free", true);
        // no boteco (foco em bebida alcoólica) o post fica 18+ sozinho
        assertThat(publish(chef, post("EVENTO", "Harmonização", "startsAt", starts, "endsAt", ends, "priceCents",
                12000, "establishmentId", boteco)).getBody()).containsEntry("ageRating", "AGE_18")
                .containsEntry("ageRatingLabel", "18+");

        // alerta sanitário: só a conta oficial verificada, com a fonte https; vai para o feed ALERT
        Map<String, Object> alertBody = Map.of("product", "Queijo minas frescal", "brand", "Laticínio Exemplo",
                "lots", List.of("L123", "L124"), "reason", "Listeria acima do limite",
                "sourceUrl", "https://www.gov.br/anvisa/pt-br/alerta");
        assertThat(status(publish(restaurant, post("ALERTA_SANITARIO", "Recall", "alert", alertBody)))).isEqualTo(403);
        Map<String, Object> alert = publish(official, post("ALERTA_SANITARIO", "Recolhimento de queijo",
                "alert", alertBody, "foodFormat", "CERVEJA")).getBody();
        assertThat(alert).containsEntry("feed", "ALERT").containsEntry("ageRating", "LIVRE");
        assertThat(map(alert, "alert")).containsEntry("sourceHost", "www.gov.br").containsEntry("brand", "Laticínio Exemplo");
        assertThat(map(alert, "interactions")).containsEntry("likes", false).containsEntry("comments", false)
                .containsEntry("shares", true);
        assertThat(errors(publish(official, post("ALERTA_SANITARIO", "Sem fonte", "alert",
                Map.of("product", "X", "brand", "Y", "lots", List.of("1"), "reason", "Z", "sourceUrl", "http://x.br")))))
                .contains("https");

        // menor não publica conteúdo de bebida alcoólica
        assertThat(errors(publish(minor17, post("RECEITA", "Caipirinha", "foodFormat", "COQUETEIS_DESTILADOS",
                "recipe", Map.of("ingredients", List.of("1 limão"), "steps", List.of("Amasse")))))).contains("18+");
        // e o boteco nem existe para ele
        assertThat(errors(publish(minor17, post("PERGUNTA", "Vale a pena?", "establishmentId", boteco))))
                .contains("Estabelecimento não encontrado");
    }

    @Test
    void authorEditsArchivesRestoresAndDeletesOwnPost() {
        String id = publishId(chef, post("RECEITA", "Pão de fermentação natural", "recipe",
                Map.of("ingredients", List.of("500 g de farinha", "350 ml de água"), "steps", List.of("Misture", "Asse"),
                        "servings", 8, "prepMinutes", 120)));
        String path = "/api/v1/posts/" + id;
        Map<String, Object> detail = get(path, null).getBody();
        assertThat(map(detail, "recipe")).containsEntry("servings", 8);
        assertThat((List<?>) map(detail, "recipe").get("steps")).hasSize(2);
        assertThat(map(detail, "kind")).containsEntry("border", "TRACEJADA");

        Map<String, Object> edited = send(HttpMethod.PATCH, path, chef, Map.of("title", "Pão de levain",
                "allergens", Map.of("TRIGO", "CONTEM"))).getBody();
        assertThat(edited).containsEntry("title", "Pão de levain");
        assertThat(map(edited, "recipe")).containsEntry("servings", 8);        // o que não veio no PATCH fica
        assertThat(list(edited, "allergens")).hasSize(1);
        assertThat(errors(send(HttpMethod.PATCH, path, chef, Map.of("kind", "PERGUNTA")))).contains("apague e publique");
        assertThat(errors(send(HttpMethod.PATCH, path, chef, Map.of("priceCents", "caro")))).contains("formato errado");
        assertThat(status(send(HttpMethod.PATCH, path, ana, Map.of("title", "invadido")))).isEqualTo(404);

        assertThat(send(HttpMethod.POST, path + "/archive", chef, null).getBody()).containsEntry("status", "ARCHIVED");
        assertThat(status(send(HttpMethod.POST, path + "/archive", chef, null))).isEqualTo(409);
        assertThat(get(path, ana).getBody()).containsEntry("status", "ARCHIVED");
        assertThat(titles(get("/api/v1/posts", ana).getBody())).doesNotContain("Pão de levain");
        assertThat(send(HttpMethod.POST, path + "/restore", chef, null).getBody()).containsEntry("status", "PUBLISHED");
        assertThat(status(send(HttpMethod.POST, path + "/restore", chef, null))).isEqualTo(409);
        assertThat(status(send(HttpMethod.DELETE, path, ana, null))).isEqualTo(404);
        assertThat(status(send(HttpMethod.DELETE, path, chef, null))).isEqualTo(204);
        assertThat(status(get(path, chef))).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT title FROM post WHERE id = ?::uuid", String.class, id))
                .isEqualTo("Conteúdo removido");
        assertThat(jdbc.queryForObject("SELECT recipe IS NULL FROM post WHERE id = ?::uuid", Boolean.class, id)).isTrue();
    }

    @Test
    void feedPagesByCursorAndHidesExpiredAdultPrivateAndInactiveContent() {
        String first = publishId(ana, post("PERGUNTA", "Primeiro"));
        publishId(chef, post("PERGUNTA", "Segundo", "establishmentId", mineiro));
        publishId(creator, post("PERGUNTA", "Terceiro"));
        String beer = publishId(creator, post("PERGUNTA", "Qual cerveja?", "foodFormat", "CERVEJA"));
        String daily = publishId(restaurant, post("PRATO_DO_DIA", "Encerrado hoje", "establishmentId", mineiro,
                "priceCents", 2500));
        publishId(teen, post("PERGUNTA", "Da Duda"));        // perfil privado: só ela vê
        publishId(official, post("ALERTA_SANITARIO", "Um alerta", "alert", Map.of("product", "X", "brand", "Y",
                "lots", List.of("1"), "reason", "Z", "sourceUrl", "https://www.gov.br/anvisa")));

        // o prato do dia passou das 23h59: sai do feed, mas a página continua com "encerrado"
        jdbc.update("UPDATE post SET expires_at = now() - interval '1 minute' WHERE id = ?::uuid", daily);
        assertThat(get("/api/v1/posts/" + daily, null).getBody()).containsEntry("expired", true);

        Map<String, Object> page1 = get("/api/v1/posts?size=2", ana).getBody();
        assertThat(titles(page1)).containsExactly("Qual cerveja?", "Terceiro");
        assertThat(map(list(page1, "items").getFirst(), "kind")).containsEntry("code", "PERGUNTA");
        assertThat(list(page1, "items").getFirst()).containsKey("detailPath").doesNotContainKey("body");
        Map<String, Object> page2 = get("/api/v1/posts?size=2&cursor=" + page1.get("nextCursor"), ana).getBody();
        assertThat(titles(page2)).containsExactly("Segundo", "Primeiro");
        assertThat(page2.get("nextCursor")).isNull();
        assertThat(status(get("/api/v1/posts?cursor=lixo", null))).isEqualTo(400);
        assertThat(status(get("/api/v1/posts?feed=PROFILE", null))).isEqualTo(400);
        assertThat(titles(get("/api/v1/posts?feed=ALERT", null).getBody())).containsExactly("Um alerta");
        assertThat(titles(get("/api/v1/posts?kind=PRATO_DO_DIA", ana).getBody())).isEmpty();

        // 18+: sai do feed do visitante e do menor; a página pede login (401) ou some (404)
        assertThat(titles(get("/api/v1/posts", null).getBody())).doesNotContain("Qual cerveja?").contains("Primeiro");
        assertThat(titles(get("/api/v1/posts", minor17).getBody())).doesNotContain("Qual cerveja?");
        ResponseEntity<Map> anonymous = get("/api/v1/posts/" + beer, null);
        assertThat(status(anonymous)).isEqualTo(401);
        assertThat((String) anonymous.getBody().get("detail")).contains("verifique a idade").contains("maiores de 18");
        assertThat(status(get("/api/v1/posts/" + beer, minor17))).isEqualTo(404);
        assertThat(get("/api/v1/posts/" + beer, ana).getBody()).containsEntry("ageRating", "AGE_18");
        // RF17: maior pela data declarada, mas sem a idade verificada: fora do feed e 403 com o pedido de verificação
        assertThat(titles(get("/api/v1/posts", restaurant).getBody())).doesNotContain("Qual cerveja?").contains("Primeiro");
        ResponseEntity<Map> unverified = get("/api/v1/posts/" + beer, restaurant);
        assertThat(status(unverified)).isEqualTo(403);
        assertThat(unverified.getBody()).containsEntry("ageVerificationRequired", true);

        // perfil privado: o post da Duda só aparece para ela
        assertThat(titles(get("/api/v1/posts", ana).getBody())).doesNotContain("Da Duda");
        assertThat(titles(get("/api/v1/posts", teen).getBody())).contains("Da Duda");

        // posts sobre um lugar: as regras de 18+ da página do lugar valem aqui também
        assertThat(titles(get("/api/v1/establishments/" + mineiro + "/posts", null).getBody())).containsExactly("Segundo");
        assertThat(titles(get("/api/v1/posts?establishmentId=" + mineiro, null).getBody())).containsExactly("Segundo");
        assertThat(status(get("/api/v1/establishments/" + boteco + "/posts", null))).isEqualTo(401);
        assertThat(status(get("/api/v1/establishments/" + boteco + "/posts", minor17))).isEqualTo(404);

        // conta suspensa: os posts dela somem (a mesma resposta de "não existe")
        jdbc.update("UPDATE user_account SET account_status = 'SUSPENDED' WHERE id = ?", ana.getId());
        assertThat(status(get("/api/v1/posts/" + first, chef))).isEqualTo(404);
        assertThat(titles(get("/api/v1/posts", chef).getBody())).doesNotContain("Primeiro");
    }

    @Test
    void trendingRanksByInteractionsAndOnlyShowsWhatTheViewerCanSee() {
        publishId(ana, post("PERGUNTA", "Calmo"));
        String hot = publishId(creator, post("PERGUNTA", "Em alta"));
        String beer = publishId(chef, post("PERGUNTA", "Chope?", "foodFormat", "CERVEJA"));
        for (var who : List.of(ana, chef, community)) {
            send(HttpMethod.PUT, "/api/v1/posts/" + hot + "/like", who, null);
            send(HttpMethod.PUT, "/api/v1/posts/" + beer + "/save", who, null);
        }
        send(HttpMethod.POST, "/api/v1/posts/" + hot + "/shares", ana, Map.of("channel", "WHATSAPP"));

        assertThat(titles(get("/api/v1/posts/trending", ana).getBody())).containsExactly("Em alta", "Chope?", "Calmo");
        assertThat(titles(get("/api/v1/posts/trending", null).getBody())).containsExactly("Em alta", "Calmo");
        assertThat(titles(get("/api/v1/posts/trending?category=CRIADOR", null).getBody())).containsExactly("Em alta");

        // o recálculo aplica o decaimento por idade e mantém a ordem pelo engajamento
        assertThat(trending.recalculate()).isEqualTo(3);
        assertThat(titles(get("/api/v1/posts/trending", ana).getBody())).containsExactly("Em alta", "Chope?", "Calmo");

        // arquivado sai do ranking
        send(HttpMethod.POST, "/api/v1/posts/" + hot + "/archive", creator, null);
        assertThat(titles(get("/api/v1/posts/trending", ana).getBody())).containsExactly("Chope?", "Calmo");
    }

    @Test
    void frameFromTheCatalogIsValidatedAndResolvedButAlertsNeverHaveOne() {
        Map<String, Object> created = publish(ana, post("PERGUNTA", "Com moldura",
                "background", Map.of("aura", Map.of("presetId", "toalha-xadrez")))).getBody();
        assertThat(map(created, "background")).containsEntry("artKind", "AURA");
        String id = (String) created.get("id");
        assertThat(map(get("/api/v1/posts/" + id, null).getBody(), "background")).containsEntry("artKind", "AURA");
        assertThat(map(list(get("/api/v1/posts", null).getBody(), "items").getFirst(), "background"))
                .containsEntry("artKind", "AURA");

        assertThat(status(publish(ana, post("PERGUNTA", "CSS?", "background", Map.of("gradient", "url(x)")))))
                .isEqualTo(422);
        assertThat(status(publish(official, post("ALERTA_SANITARIO", "Recall", "background",
                Map.of("aura", Map.of("presetId", "toalha-xadrez")), "alert", Map.of("product", "X", "brand", "Y",
                        "lots", List.of("1"), "reason", "Z", "sourceUrl", "https://www.gov.br/anvisa")))))
                .isEqualTo(422);

        // "background": null no PATCH volta à moldura padrão
        Map<String, Object> clear = new HashMap<>();
        clear.put("background", null);
        assertThat(send(HttpMethod.PATCH, "/api/v1/posts/" + id, ana, clear).getBody().get("background")).isNull();
        assertThat(jdbc.queryForObject("SELECT background IS NULL FROM post WHERE id = ?::uuid", Boolean.class, id))
                .isTrue();
    }

    @Test
    void vocabularyListsKindsAllergensAndWhatTheViewerCanPublish() {
        Map<String, Object> anon = new ApiClient(port).get("/api/v1/posts/vocabulary").getBody();
        assertThat(list(anon, "kinds")).hasSize(7);
        assertThat(list(anon, "allergens")).hasSize(26).anyMatch(a -> "GERGELIM".equals(a.get("code"))
                && "PROPOSTO".equals(a.get("status")));
        assertThat(list(anon, "warnings")).extracting(w -> w.get("code")).containsExactly("LACTOSE", "GLUTEN");
        assertThat((List<?>) anon.get("publishableKinds")).isEmpty();
        assertThat((List<Object>) get("/api/v1/posts/vocabulary", official).getBody().get("publishableKinds"))
                .containsExactly("FOTO_PRATO", "RECEITA", "ALERTA_SANITARIO");
        assertThat((List<Object>) get("/api/v1/posts/vocabulary", ana).getBody().get("publishableKinds"))
                .containsExactly("FOTO_PRATO", "RECEITA", "PERGUNTA");
    }
}
