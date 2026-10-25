package com.friendlyeats.config;

import com.friendlyeats.domain.model.CategoryVerification;
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
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Dados de exemplo só no perfil {@code dev} (nunca em produção): contas, lugares em São Paulo, Belo Horizonte e
 * Recife e avaliações, para o app web ter o que mostrar sem a moderação (RF13) — os selos já nascem conferidos.
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

    public DevSeed(UserAccountRepository users, EstablishmentRepository establishments, ReviewRepository reviews,
                   PasswordEncoder passwords) {
        this.users = users;
        this.establishments = establishments;
        this.reviews = reviews;
        this.passwords = passwords;
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
        for (Place p : places) {
            Establishment e = place(owner.getId(), p, AddressVisibility.ADDR_PUBLIC_FULL);
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
        log.info("Seed de desenvolvimento: {} lugares e {} contas. Entre com ana@friendlyeats.dev, "
                + "dono@friendlyeats.dev ou lu@friendlyeats.dev (senha em DevSeed.PASSWORD).", places.size() + 1, users.count());
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
