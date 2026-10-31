package com.friendlyeats.api;

import com.friendlyeats.application.account.AccountService;
import com.friendlyeats.application.media.MediaStorage;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.support.ApiClient;
import com.friendlyeats.support.CapturingNotifier;
import com.friendlyeats.support.GarageContainer;
import com.friendlyeats.support.PolyglotContainers;
import com.friendlyeats.support.TestImages;
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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ponta a ponta com o Garage (API S3) real: envio pré-assinado da foto de perfil (RF2), limpeza sem EXIF/GPS, troca e
 * remoção, regras do envio e a foto apagada do bucket quando a conta é eliminada (RF5).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@Import({PolyglotContainers.class, CapturingNotifier.class})
class ProfilePhotoApiIT {

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

    final Map<Long, ApiClient> browsers = new HashMap<>();
    UserAccount ana;
    UserAccount other;
    UserAccount pending;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE TABLE user_account RESTART IDENTITY CASCADE");
        redis.getRequiredConnectionFactory().getConnection().serverCommands().flushDb();
        browsers.clear();
        ana = save(active("Ana Ribeiro", "ana@example.com", UserCategoryEnum.COMENSAL, LocalDate.of(1994, 5, 2)));
        other = save(active("João Martins", "joao@example.com", UserCategoryEnum.CHEF, LocalDate.of(1990, 1, 1)));
        pending = save(new UserAccount("Pendente", "pend@example.com", passwords.encode(PASSWORD), UserCategoryEnum.COMENSAL));
    }

    @Test
    void profilePhotoIsASquareWithoutExifAndTheOldOneLeavesTheBucket() throws Exception {
        String first = uploadPhoto(ana, TestImages.jpegWithExif(1600, 800, 6), "image/jpeg");
        assertThat(first).startsWith("uploads/" + ana.getId() + "/").endsWith(".jpg");
        ResponseEntity<Map> set = send(HttpMethod.PUT, "/api/v1/me/photo", ana, Map.of("objectKey", first));
        assertThat(set.getStatusCode().value()).isEqualTo(200);
        String avatarKey = (String) set.getBody().get("avatarObjectKey");
        assertThat(avatarKey).startsWith("avatars/").endsWith(".jpg");
        assertThat((String) set.getBody().get("avatarUrl")).endsWith("/" + avatarKey);
        assertThat(storage.head(first)).isEmpty();

        // recorte central quadrado de 512 px, já "em pé" (Orientation 6: a metade vermelha sobe) e sem EXIF
        byte[] avatar = storage.read(avatarKey);
        assertThat(new String(avatar, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("GPS");
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(avatar));
        assertThat(img.getWidth()).isEqualTo(512);
        assertThat(img.getHeight()).isEqualTo(512);
        assertThat((img.getRGB(256, 40) >> 16) & 0xFF).isGreaterThan(200);
        assertThat(img.getRGB(256, 470) & 0xFF).isGreaterThan(200);

        // a chave é de Ana: outra conta não usa (mesma resposta de "não existe"); a chave já usada também não serve
        String anasUpload = uploadPhoto(ana, TestImages.png(64, 64, false), "image/png");
        assertThat(send(HttpMethod.PUT, "/api/v1/me/photo", other, Map.of("objectKey", anasUpload))
                .getStatusCode().value()).isEqualTo(422);
        assertThat(send(HttpMethod.PUT, "/api/v1/me/photo", ana, Map.of("objectKey", first))
                .getStatusCode().value()).isEqualTo(422);

        // trocar a foto apaga a anterior
        String second = uploadPhoto(ana, TestImages.png(64, 64, true), "image/png");
        String newKey = (String) send(HttpMethod.PUT, "/api/v1/me/photo", ana, Map.of("objectKey", second))
                .getBody().get("avatarObjectKey");
        assertThat(newKey).startsWith("avatars/").endsWith(".png");
        assertThat(storage.head(avatarKey)).isEmpty();

        Map<String, Object> removed = send(HttpMethod.DELETE, "/api/v1/me/photo", ana, null).getBody();
        assertThat(removed.get("avatarObjectKey")).isNull();
        assertThat(removed.get("avatarUrl")).isNull();
        assertThat(storage.head(newKey)).isEmpty();
        assertThat(send(HttpMethod.PUT, "/api/v1/me/photo", null, Map.of("objectKey", second)).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    void uploadRulesSignedSizeAndFilesThatAreNotPhotos() throws Exception {
        assertThat(send(HttpMethod.POST, "/api/v1/media/uploads", null,
                Map.of("kind", "IMAGE", "contentType", "image/png", "sizeBytes", 10)).getStatusCode().value()).isEqualTo(401);
        assertThat(send(HttpMethod.POST, "/api/v1/media/uploads", pending,
                Map.of("kind", "IMAGE", "contentType", "image/png", "sizeBytes", 10)).getStatusCode().value()).isEqualTo(403);
        ResponseEntity<Map> bad = send(HttpMethod.POST, "/api/v1/media/uploads", ana,
                Map.of("kind", "IMAGE", "contentType", "image/gif", "sizeBytes", 20 * 1024 * 1024));
        assertThat(bad.getStatusCode().value()).isEqualTo(422);
        assertThat(list(bad.getBody(), "errors").toString()).contains("JPEG ou PNG").contains("10 MB");
        assertThat(send(HttpMethod.POST, "/api/v1/media/uploads", ana,
                Map.of("kind", "VIDEO", "contentType", "video/mp4", "sizeBytes", 1000)).getStatusCode().value()).isEqualTo(422);

        // o tamanho declarado vai na assinatura: mandar outro arquivo é recusado pelo próprio bucket
        byte[] fake = "não sou uma foto".getBytes(StandardCharsets.UTF_8);
        Map<String, Object> ticket = send(HttpMethod.POST, "/api/v1/media/uploads", ana,
                Map.of("kind", "IMAGE", "contentType", "image/png", "sizeBytes", fake.length)).getBody();
        assertThat(putToBucket(ticket, TestImages.png(32, 32, false))).isEqualTo(403);
        assertThat(putToBucket(ticket, fake)).isEqualTo(200);

        ResponseEntity<Map> notPhoto = send(HttpMethod.PUT, "/api/v1/me/photo", ana, Map.of("objectKey", ticket.get("objectKey")));
        assertThat(notPhoto.getStatusCode().value()).isEqualTo(422);
        assertThat(list(notPhoto.getBody(), "errors").toString()).contains("não é uma foto");
        assertThat(jdbc.queryForObject("SELECT photo_object_key FROM user_account WHERE id = ?", String.class, ana.getId()))
                .isNull();
    }

    @Test
    void accountEliminationDeletesTheProfilePhotoFromTheBucket() throws Exception {
        String avatarKey = (String) send(HttpMethod.PUT, "/api/v1/me/photo", ana, Map.of("objectKey",
                uploadPhoto(ana, TestImages.png(64, 64, false), "image/png"))).getBody().get("avatarObjectKey");
        assertThat(storage.head(avatarKey)).isPresent();

        // RF5: carência vencida → a saga anonimiza a conta e, depois do commit, apaga a foto do bucket
        jdbc.update("UPDATE user_account SET account_status = 'PENDING_DELETION', "
                + "deletion_scheduled_for = now() - interval '1 minute' WHERE id = ?", ana.getId());
        accounts.eliminate(ana.getId());
        assertThat(jdbc.queryForObject("SELECT photo_object_key FROM user_account WHERE id = ?", String.class, ana.getId()))
                .isNull();
        assertThat(storage.head(avatarKey)).isEmpty();
    }

    // ------------------------------------------------------------ apoio

    /** Pede o tíquete e envia o arquivo; devolve a chave em {@code uploads/}. */
    private String uploadPhoto(UserAccount who, byte[] bytes, String contentType) throws Exception {
        Map<String, Object> ticket = send(HttpMethod.POST, "/api/v1/media/uploads", who,
                Map.of("kind", "IMAGE", "contentType", contentType, "sizeBytes", bytes.length)).getBody();
        // o original (com EXIF/GPS) vai para o bucket privado de uploads, nunca para o de mídias públicas
        assertThat((String) ticket.get("uploadUrl")).contains("/" + GarageContainer.UPLOADS_BUCKET + "/uploads/")
                .doesNotContain("/" + GarageContainer.BUCKET + "/");
        assertThat(putToBucket(ticket, bytes)).isEqualTo(200);
        return (String) ticket.get("objectKey");
    }

    /** O que o app faz com o tíquete: PUT direto no bucket, com os cabeçalhos assinados. */
    @SuppressWarnings("unchecked")
    private static int putToBucket(Map<String, Object> ticket, byte[] bytes) throws Exception {
        HttpRequest.Builder put = HttpRequest.newBuilder(URI.create((String) ticket.get("uploadUrl")))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        ((Map<String, String>) ticket.get("headers")).forEach((name, value) -> {
            if (!name.equalsIgnoreCase("content-length")) {         // o cliente HTTP calcula sozinho
                put.header(name, value);
            }
        });
        return HttpClient.newHttpClient().send(put.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private UserAccount active(String name, String email, UserCategoryEnum type, LocalDate birth) {
        UserAccount user = new UserAccount(name, email, passwords.encode(PASSWORD), type);
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

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> parent, String key) {
        return (List<Map<String, Object>>) parent.get(key);
    }
}
