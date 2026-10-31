package com.friendlyeats.config;

import com.friendlyeats.application.media.MediaStorage;
import com.friendlyeats.application.post.PostRules;
import com.friendlyeats.domain.model.AgeRating;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.MediaType;
import com.friendlyeats.domain.model.Post;
import com.friendlyeats.domain.model.PostComment;
import com.friendlyeats.domain.model.PostKind;
import com.friendlyeats.domain.model.ReactionKind;
import com.friendlyeats.domain.model.UuidV7;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.AddressVisibility;
import com.friendlyeats.domain.taxonomy.Approach;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.GeoPrecision;
import com.friendlyeats.domain.taxonomy.ReviewDimension;
import com.friendlyeats.domain.taxonomy.VisitMode;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostCommentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.PostRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Dados de exemplo só no perfil {@code dev} (nunca em produção): contas, lugares em São Paulo, Belo Horizonte e
 * Recife, avaliações e um post de cada tipo com curtidas e comentários (RF9, RF12), para o app web ter o que mostrar
 * sem a moderação (RF13) — os selos já nascem conferidos. Duas contas de moderação (RF13) recebem o papel MODERATOR: uma
 * decide e a outra revê o recurso.
 * Roda só com o banco vazio. As senhas são públicas de propósito: valem apenas na sua máquina.
 */
@Component
@Profile("dev")
public class DevSeed implements ApplicationRunner {

    /** Senha de todas as contas de exemplo (só no perfil dev). */
    public static final String PASSWORD = "uma frase longa de exemplo";

    private static final Logger log = LoggerFactory.getLogger(DevSeed.class);

    private final UserAccountRepository users;
    private final EstablishmentRepository establishments;
    private final ReviewRepository reviews;
    private final PasswordEncoder passwords;
    private final PostRepository posts;
    private final PostCommentRepository comments;
    private final MediaStorage storage;
    private final UserRoleRepository roles;

    public DevSeed(UserAccountRepository users, EstablishmentRepository establishments, ReviewRepository reviews,
                   PasswordEncoder passwords, PostRepository posts, PostCommentRepository comments,
                   MediaStorage storage, UserRoleRepository roles) {
        this.roles = roles;
        this.users = users;
        this.establishments = establishments;
        this.reviews = reviews;
        this.passwords = passwords;
        this.posts = posts;
        this.comments = comments;
        this.storage = storage;
    }

    private record Place(String name, EstablishmentType type, Cuisine cuisine, FoodFormat format,
                         Map<DietaryStyle, DietaryStyle.Level> dietary, int minCents, int maxCents, String uf,
                         String ibge, String city, String neighborhood, String street, double lat, double lng,
                         int[] ratings) {
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String hash = passwords.encode(PASSWORD);
        UserAccount ana = account("Ana Ribeiro", "ana", "ana@friendlyeats.dev", UserCategoryEnum.COMENSAL,
                CategoryVerification.NOT_REQUIRED, Map.of(), hash);
        UserAccount owner = account("Rede Sabores", "rede.sabores", "dono@friendlyeats.dev",
                UserCategoryEnum.ESTABELECIMENTO, CategoryVerification.VERIFIED, Map.of("cnpj", "11222333000181",
                        "legalName", "Rede Sabores Ltda", "tradeName", "Rede Sabores", "cnae", "5611-2/01",
                        "city", "São Paulo", "state", "SP"), hash);
        UserAccount lu = account("Marmitas da Lu", "marmitas.lu", "lu@friendlyeats.dev",
                UserCategoryEnum.ESTABELECIMENTO, CategoryVerification.VERIFIED, Map.of("informalFormat",
                        "COZINHA_CASEIRA", "cpf", "52998224725", "tradeName", "Marmitas da Lu", "city", "Recife",
                        "state", "PE"), hash);
        account("Empório Bom", "emporio.bom", "loja@friendlyeats.dev", UserCategoryEnum.VAREJO,
                CategoryVerification.PENDING, Map.of("segment", "EMPORIO"), hash);

        List<UserAccount> people = new ArrayList<>();
        people.add(ana);
        for (int i = 1; i <= 12; i++) {
            people.add(account("Pessoa " + i, "pessoa" + i, "pessoa" + i + "@friendlyeats.dev",
                    UserCategoryEnum.COMENSAL, CategoryVerification.NOT_REQUIRED, Map.of(), hash));
        }

        List<Place> places = List.of(
                new Place("Sabor Mineiro", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.MINEIRA,
                        FoodFormat.PRATO_FEITO, Map.of(), 4500, 6500, "SP", "3550308", "São Paulo", "Pinheiros",
                        "Rua dos Pinheiros", -23.5666, -46.6890, new int[]{5, 4, 4, 5, 3, 5, 4}),
                new Place("Dendê Vegano", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.BAIANA, null,
                        Map.of(DietaryStyle.VEGANO, DietaryStyle.Level.EXCLUSIVO), 3500, 5500, "SP", "3550308",
                        "São Paulo", "Vila Madalena", "Rua Aspicuelta", -23.5560, -46.6900,
                        new int[]{5, 5, 4, 5, 5, 4, 5, 5, 4, 5, 5, 5}),
                new Place("Cantina Nonna", EstablishmentType.RESTAURANTE_A_LA_CARTE, Cuisine.ITALIANA,
                        FoodFormat.PIZZA, Map.of(DietaryStyle.SEM_GLUTEN, DietaryStyle.Level.OFERECE), 9000, 14000,
                        "SP", "3550308", "São Paulo", "Bixiga", "Rua 13 de Maio", -23.5590, -46.6460,
                        new int[]{4, 3}),
                new Place("Temakeria Sol", EstablishmentType.FAST_CASUAL, Cuisine.JAPONESA, FoodFormat.SUSHI,
                        Map.of(), 5000, 8000, "SP", "3550308", "São Paulo", "Liberdade", "Rua Galvão Bueno",
                        -23.5570, -46.6350, new int[]{}),
                new Place("Pão da Esquina", EstablishmentType.PADARIA, null, FoodFormat.PAES, Map.of(), 1500, 3000,
                        "SP", "3550308", "São Paulo", "Moema", "Avenida Ibirapuera", -23.6000, -46.6650,
                        new int[]{5, 4, 5, 4, 5}),
                new Place("Boteco da Esquina", EstablishmentType.BOTECO, null, FoodFormat.PETISCOS_BOTECO, Map.of(),
                        5000, 8000, "SP", "3550308", "São Paulo", "Vila Madalena", "Rua Mourato Coelho", -23.5585,
                        -46.6890, new int[]{4, 5, 4, 3, 4, 5}),
                new Place("Feijão de Corda", EstablishmentType.RESTAURANTE_QUILO, Cuisine.NORDESTINA,
                        FoodFormat.PRATO_FEITO, Map.of(), 3000, 5000, "MG", "3106200", "Belo Horizonte", "Savassi",
                        "Rua Pernambuco", -19.9370, -43.9330, new int[]{4, 4, 5, 3, 4}),
                new Place("Taco Loco", EstablishmentType.FOOD_TRUCK, Cuisine.MEXICANA, FoodFormat.TACOS_BURRITOS,
                        Map.of(), 2500, 4000, "MG", "3106200", "Belo Horizonte", "Lourdes", "Praça da Liberdade",
                        -19.9320, -43.9380, new int[]{5}),
                new Place("Açaí do Norte", EstablishmentType.ACAITERIA_SUCOS, Cuisine.PARAENSE, FoodFormat.ACAI,
                        Map.of(DietaryStyle.VEGANO, DietaryStyle.Level.OFERECE), 2000, 3500, "PE", "2611606",
                        "Recife", "Boa Viagem", "Avenida Boa Viagem", -8.1189, -34.9010, new int[]{5, 5, 4, 5, 5}));

        Instant now = Instant.now();
        int author = 0;
        Map<String, Establishment> byName = new java.util.HashMap<>();
        for (Place p : places) {
            Establishment e = place(owner.getId(), p, AddressVisibility.ADDR_PUBLIC_FULL);
            byName.put(p.name(), e);
            for (int i = 0; i < p.ratings().length; i++) {
                UserAccount who = people.get(author++ % people.size());
                Review r = new Review(e.getId(), who.getId());
                r.write(p.ratings()[i], i % 3 == 2 ? VisitMode.DELIVERY : VisitMode.DINE_IN,
                        Map.of(ReviewDimension.COMIDA, p.ratings()[i]), p.minCents() + 500 * (i % 4), null,
                        now.minus(Duration.ofDays(20L * i)));
                reviews.save(r);
            }
        }
        place(lu.getId(), new Place("Marmitas da Lu", EstablishmentType.MARMITARIA, null, FoodFormat.PRATO_FEITO,
                Map.of(), 2000, 3000, "PE", "2611606", "Recife", "Boa Viagem", "Rua da Casa", -8.1220, -34.9050,
                new int[]{}), AddressVisibility.ADDR_PUBLIC_BAIRRO);
        UserAccount chef = account("Rita Duarte", "chef.rita", "chef@friendlyeats.dev", UserCategoryEnum.CHEF,
                CategoryVerification.NOT_REQUIRED, Map.of("role", "CHEF", "city", "Belo Horizonte", "state", "MG"), hash);
        UserAccount vigilancia = account("Vigilância Sanitária de Exemplo", "vigilancia.exemplo",
                "alertas@friendlyeats.dev", UserCategoryEnum.OFICIAL, CategoryVerification.VERIFIED,
                Map.of("agency", "Vigilância sanitária municipal (exemplo)", "city", "São Paulo", "state", "SP"), hash);
        seedPosts(now, ana, chef, owner, vigilancia, people, byName);
        for (String n : List.of("", "2")) {
            UserAccount mod = account("Moderação " + (n.isEmpty() ? "1" : n), "moderacao" + n,
                    "moderacao" + n + "@friendlyeats.dev", UserCategoryEnum.COMENSAL, CategoryVerification.NOT_REQUIRED,
                    Map.of(), hash);
            roles.grant(mod.getId(), UserRoleRepository.MODERATOR, "seed");
        }
        log.info("Seed de desenvolvimento: {} lugares e {} contas. Entre com ana@friendlyeats.dev, "
                + "dono@friendlyeats.dev, lu@friendlyeats.dev, chef@friendlyeats.dev, alertas@friendlyeats.dev ou, "
                + "na moderação, moderacao@friendlyeats.dev e moderacao2@friendlyeats.dev (senha em DevSeed.PASSWORD).",
                places.size() + 1, users.count());
    }

    /** Um post de cada tipo (RF9), com curtidas e comentários (RF12). */
    private void seedPosts(Instant now, UserAccount ana, UserAccount chef, UserAccount owner, UserAccount vigilancia,
                           List<UserAccount> people, Map<String, Establishment> places) {
        Post photo = post(ana, PostKind.FOTO_PRATO, "Pão de queijo saindo do forno", now.minus(Duration.ofHours(5)), p -> {
            p.setBody("Casquinha crocante e miolo puxa-puxa. Peça o da fornada das 16h.");
            p.setMediaType(MediaType.IMAGE);
            p.setMediaObjectKey(examplePhoto());
            p.setAltText("Cesta de palha com seis pães de queijo dourados sobre uma toalha xadrez.");
            p.setEstablishmentId(places.get("Pão da Esquina").getId());
            p.setCuisine(Cuisine.MINEIRA.name());
            p.setFoodFormat(FoodFormat.SALGADOS.name());
            p.setDietaryStyles(List.of(DietaryStyle.SEM_GLUTEN.name()));
            p.setAllergens(Map.of("LEITE", "CONTEM", "OVOS", "CONTEM"));
            p.setFoodWarnings(List.of("LACTOSE"));
            p.setPriceCents(800);
        });
        Post recipe = post(chef, PostKind.RECEITA, "Feijão tropeiro da Rita", now.minus(Duration.ofHours(30)), p -> {
            p.setBody("O segredo é dourar a farinha na gordura do bacon.");
            p.setCuisine(Cuisine.MINEIRA.name());
            p.setFoodFormat(FoodFormat.PRATO_FEITO.name());
            p.setAllergens(Map.of("OVOS", "CONTEM"));
            p.setRecipe(new Post.Recipe(List.of("500 g de feijão-carioca cozido", "200 g de bacon em cubos",
                    "2 xícaras de farinha de mandioca", "3 ovos", "1 maço de couve"),
                    List.of("Doure o bacon até soltar a gordura.", "Mexa os ovos na mesma panela.",
                            "Junte o feijão escorrido e a farinha aos poucos.", "Finalize com a couve refogada."),
                    6, 40));
        });
        Post question = post(people.get(1), PostKind.PERGUNTA, "Onde tem acarajé bom em São Paulo?",
                now.minus(Duration.ofHours(2)), p -> {
                    p.setBody("De preferência com vatapá caseiro e perto do metrô.");
                    p.setCuisine(Cuisine.BAIANA.name());
                    p.setCity("São Paulo");
                });
        Post daily = post(owner, PostKind.PRATO_DO_DIA, "Hoje: tutu à mineira com torresmo", now.minus(Duration.ofHours(1)),
                p -> {
                    p.setEstablishmentId(places.get("Sabor Mineiro").getId());
                    p.setCuisine(Cuisine.MINEIRA.name());
                    p.setFoodFormat(FoodFormat.PRATO_FEITO.name());
                    p.setPriceCents(3990);
                    p.setExpiresAt(PostRules.expiresAt(PostKind.PRATO_DO_DIA, now, null));
                });
        Post promo = post(owner, PostKind.PROMOCAO, "Segunda da pizza: a grande pelo preço da média",
                now.minus(Duration.ofHours(8)), p -> {
                    p.setEstablishmentId(places.get("Cantina Nonna").getId());
                    p.setFoodFormat(FoodFormat.PIZZA.name());
                    p.setAllergens(Map.of("TRIGO", "CONTEM", "LEITE", "CONTEM"));
                    p.setStartsAt(now.minus(Duration.ofHours(8)));
                    p.setEndsAt(now.plus(Duration.ofDays(7)));
                    p.setExpiresAt(p.getEndsAt());
                    p.setPriceFromCents(9000);
                    p.setPriceToCents(6500);
                });
        Post event = post(owner, PostKind.EVENTO, "Roda de samba no boteco", now.minus(Duration.ofHours(12)), p -> {
            p.setBody("Petiscos da casa e samba ao vivo a partir das 19h.");
            p.setEstablishmentId(places.get("Boteco da Esquina").getId());
            p.setFoodFormat(FoodFormat.PETISCOS_BOTECO.name());
            p.setAgeRating(AgeRating.AGE_18);                // lugar com foco em bebida alcoólica
            p.setStartsAt(now.plus(Duration.ofDays(3)));
            p.setEndsAt(now.plus(Duration.ofDays(3)).plus(Duration.ofHours(5)));
            p.setExpiresAt(p.getEndsAt());
            p.setFreeEntry(true);
        });
        Post alert = post(vigilancia, PostKind.ALERTA_SANITARIO, "Recolhimento de doce de leite (dado de exemplo)",
                now.minus(Duration.ofHours(3)), p -> {
                    p.setBody("Não consuma o produto dos lotes abaixo. Dado fictício, só para desenvolvimento.");
                    p.setAllergens(Map.of("LEITE", "CONTEM"));
                    p.setFoodAlert(new Post.FoodAlert("Doce de leite pastoso 400 g", "Marca Fictícia",
                            List.of("L2610A", "L2610B"), "Amido não declarado no rótulo.",
                            "https://www.gov.br/anvisa/pt-br"));
                });

        List<Post> all = List.of(photo, recipe, question, daily, promo, event, alert);
        for (int i = 0; i < all.size(); i++) {
            Post p = all.get(i);
            int likes = p.getKind() == PostKind.ALERTA_SANITARIO ? 0 : 3 + i % 4;
            for (int j = 0; j < likes; j++) {
                if (posts.insertReaction(p.getId(), people.get(2 + j).getId(), ReactionKind.LIKE.name()) == 1) {
                    posts.addToLikeCount(p.getId(), 1);
                }
            }
        }
        comment(photo, people.get(3), "Melhor pão de queijo do bairro!");
        comment(photo, ana, "Concordo, e o café de lá também é ótimo.");
        comment(recipe, people.get(4), "Fiz no domingo e ficou perfeito.");
        comment(question, people.get(5), "Tem uma baiana ótima na saída do metrô Liberdade.");
        comment(promo, people.get(6), "Vale para entrega também?");
        if (posts.insertShare(alert.getId(), people.get(7).getId(), "WHATSAPP") == 1) {
            posts.addToShareCount(alert.getId(), 1);
        }
    }

    private Post post(UserAccount author, PostKind kind, String title, Instant at, Consumer<Post> setup) {
        Post p = new Post(author, kind, title);
        p.setCreatedAt(at);
        setup.accept(p);
        p.publish();
        return posts.saveAndFlush(p);
    }

    private void comment(Post post, UserAccount who, String body) {
        comments.save(new PostComment(post.getId(), who.getId(), body));
        posts.addToCommentCount(post.getId(), 1);
    }

    /** Foto de exemplo desenhada aqui e gravada no bucket; com o Garage fora do ar, o post fica sem a imagem. */
    private String examplePhoto() {
        String key = "posts/" + UuidV7.next() + ".jpg";
        BufferedImage img = new BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setPaint(new GradientPaint(0, 0, new Color(0xE8, 0x8F, 0x12), 800, 600, new Color(0x7F, 0x4D, 0x06)));
        g.fillRect(0, 0, 800, 600);
        g.setColor(new Color(0xF7, 0xC9, 0x48));
        for (int i = 0; i < 6; i++) {
            g.fillOval(170 + (i % 3) * 170, 170 + (i / 3) * 150, 130, 110);
        }
        g.dispose();
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", out);
            storage.write(key, out.toByteArray(), "image/jpeg");
        } catch (IOException | RuntimeException e) {
            log.warn("Foto de exemplo não foi gravada no bucket ({}); o post fica sem a imagem.", e.getMessage());
        }
        return key;
    }

    private UserAccount account(String name, String handle, String email, UserCategoryEnum type,
                                CategoryVerification seal, Map<String, Object> details, String hash) {
        UserAccount u = new UserAccount(name, email, hash, type);
        u.setHandle(handle);
        u.setUserDateOfBirth(LocalDate.of(1990, 1, 1));
        u.setTypeDetails(details);
        u.setCategoryVerification(seal);
        u.activate();
        return users.save(u);
    }

    private Establishment place(long ownerId, Place p, AddressVisibility visibility) {
        Establishment e = new Establishment(ownerId, p.name());
        e.setTypes(p.type(), List.of());
        e.classify(p.cuisine(), List.of(), p.format(), List.of(), Approach.TRADICIONAL, p.dietary());
        e.setDeclaredPrice(p.minCents(), p.maxCents(), false);
        e.setLocation(p.uf(), p.ibge(), p.city(), p.neighborhood(), p.street(), "100", null, null, p.lat(), p.lng(),
                GeoPrecision.GEO_PIN_CONFIRMED, visibility);
        return establishments.save(e);
    }
}
