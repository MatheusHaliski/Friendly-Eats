package com.friendlyeats.application.establishment;

import com.friendlyeats.application.account.AgePolicy;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentViews.ChipView;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.establishment.EstablishmentViews.PriceView;
import com.friendlyeats.application.establishment.EstablishmentViews.RatingView;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.CuisineChip;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.EstablishmentGroup;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.PriceLevel;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Busca e filtros (RF10): culinária (com as descendentes), formato, tipo, grupo, faixa de preço, nota mínima, estilo
 * alimentar, município e "perto de mim" por raio (PostGIS). Só entram páginas públicas — conta dona ativa, selo
 * conferido, lugar aberto — e, para visitante sem login ou menor de 18, nenhum lugar com foco em bebida alcoólica.
 *
 * <p>Nota e faixa de preço são filtradas e ordenadas no SQL com as mesmas fórmulas do {@link RatingCalculator} e da
 * {@link PriceScale}; os cards da página de resultados são montados pelo {@link EstablishmentService}, então mostram
 * exatamente o que a página do lugar mostra.
 *
 * <p>Privacidade: a coordenada de quem busca vive só nesta requisição (não é gravada nem vai para log). A distância
 * de um lugar com endereço aproximado (cozinha caseira) é medida até o ponto público arredondado e mostrada de 0,5 em
 * 0,5 km, para a busca não servir de trilateração da casa de alguém; lugar com endereço oculto não entra na busca por
 * raio.
 */
@Service
public class EstablishmentSearch {

    public enum Sort { RELEVANCE, DISTANCE, RATING }

    public record Query(String q, List<String> cuisines, List<String> formats, List<String> types, List<String> groups,
                        List<String> prices, Double minRating, List<String> dietary, Double lat, Double lng,
                        Double radiusKm, String municipality, String sort, Integer page, Integer size) {
    }

    public record CardLocation(String uf, String city, String neighborhood, boolean approximate) {
    }

    public record Card(long id, String name, Coded primaryType, Coded group, ChipView chip, Coded primaryCuisine,
                       Coded primaryFormat, PriceView price, RatingView rating, CardLocation location,
                       Double distanceKm, boolean adultsOnly, Establishment.Status status) {
    }

    public record Page(List<Card> items, int page, int size, long total, Double radiusKm) {
    }

    static final double DEFAULT_RADIUS_KM = 5;
    static final double MAX_RADIUS_KM = 50;
    static final int MAX_PAGE_SIZE = 50;
    /** Folga do pré-filtro pelo índice GiST: o ponto público arredondado fica a no máximo ~400 m do real. */
    static final int PREFILTER_SLACK_M = 600;
    /** Quanto a distância pesa na relevância quando há "perto de mim". */
    static final double DISTANCE_PENALTY = 0.3;

    private final NamedParameterJdbcTemplate jdbc;
    private final EstablishmentRepository establishments;
    private final ReviewRepository reviews;
    private final UserAccountRepository users;
    private final EstablishmentService pages;
    private final PriceScale prices;
    private final AgePolicy age;
    private final double priorMean;
    private final double priorWeight;
    private final int minDisplay;
    private final double halfLifeDays;
    private final double recencyFloor;
    private final int minPriceReports;

    public EstablishmentSearch(NamedParameterJdbcTemplate jdbc, EstablishmentRepository establishments,
                               ReviewRepository reviews, UserAccountRepository users, EstablishmentService pages,
                               PriceScale prices, AgePolicy age,
                               @Value("${friendly-eats.rating.prior-mean:4.2}") double priorMean,
                               @Value("${friendly-eats.rating.prior-weight:10}") double priorWeight,
                               @Value("${friendly-eats.rating.min-display:5}") int minDisplay,
                               @Value("${friendly-eats.rating.half-life-days:365}") double halfLifeDays,
                               @Value("${friendly-eats.rating.recency-floor:0.25}") double recencyFloor,
                               @Value("${friendly-eats.price.min-reports:5}") int minPriceReports) {
        this.jdbc = jdbc;
        this.establishments = establishments;
        this.reviews = reviews;
        this.users = users;
        this.pages = pages;
        this.prices = prices;
        this.age = age;
        this.priorMean = priorMean;
        this.priorWeight = priorWeight;
        this.minDisplay = minDisplay;
        this.halfLifeDays = halfLifeDays;
        this.recencyFloor = recencyFloor;
        this.minPriceReports = minPriceReports;
    }

    @Transactional(readOnly = true)
    public Page search(Optional<Long> viewerId, Query q) {
        List<String> errors = new ArrayList<>();
        Set<String> cuisines = expandCuisines(codes(q.cuisines(), Cuisine::fromCode, "cuisine", errors));
        Set<String> formats = codes(q.formats(), FoodFormat::fromCode, "format", errors);
        Set<String> types = codes(q.types(), EstablishmentType::fromCode, "type", errors);
        Set<String> groups = codes(q.groups(), c -> Arrays.stream(EstablishmentGroup.values())
                .filter(g -> g.name().equals(c)).findFirst(), "group", errors);
        Set<String> priceLevels = codes(q.prices(), c -> Arrays.stream(PriceLevel.values())
                .filter(p -> p.name().equals(c)).findFirst(), "price", errors);
        Set<String> dietary = codes(q.dietary(), DietaryStyle::fromCode, "dietary", errors);
        if (q.minRating() != null && (q.minRating() < 1 || q.minRating() > 5)) {
            errors.add("Nota mínima entre 1 e 5.");
        }
        boolean geo = q.lat() != null || q.lng() != null;
        if (geo && (q.lat() == null || q.lng() == null)) {
            errors.add("Para buscar perto de você, envie latitude e longitude juntas.");
        } else if (geo && !EstablishmentRules.insideBrazil(q.lat(), q.lng())) {
            errors.add("A busca por raio é só dentro do Brasil.");
        }
        double radius = q.radiusKm() == null ? DEFAULT_RADIUS_KM : q.radiusKm();
        if (radius <= 0 || radius > MAX_RADIUS_KM) {
            errors.add("Raio entre 0 e 50 km; para mais longe, busque pelo município.");
        }
        if (q.municipality() != null && !q.municipality().matches("^\\d{7}$")) {
            errors.add("Município pelo código IBGE de 7 dígitos.");
        }
        Sort sort = q.sort() == null || q.sort().isBlank() ? Sort.RELEVANCE
                : Arrays.stream(Sort.values()).filter(s -> s.name().equals(q.sort())).findFirst().orElse(null);
        if (sort == null) {
            errors.add("Ordem: RELEVANCE, DISTANCE ou RATING.");
        } else if (sort == Sort.DISTANCE && !geo) {
            errors.add("Para ordenar por distância, use \"perto de mim\".");
        }
        String text = q.q() == null || q.q().isBlank() ? null : q.q().strip();
        if (text != null && text.length() > 80) {
            errors.add("Busca por nome com até 80 caracteres.");
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        int size = Math.max(1, Math.min(q.size() == null ? 20 : q.size(), MAX_PAGE_SIZE));
        int page = Math.max(0, q.page() == null ? 0 : q.page());
        boolean adult = viewerId.flatMap(users::findById).map(u -> age.adult(u.getUserDateOfBirth())).orElse(false);

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("priorMean", priorMean).addValue("priorWeight", priorWeight)
                .addValue("halfLife", halfLifeDays).addValue("floor", recencyFloor)
                .addValue("minDisplay", minDisplay).addValue("minReports", minPriceReports)
                .addValue("limit", size).addValue("offset", (long) page * size);
        int[] t = prices.thresholdsCents();
        p.addValue("t1", t[0]).addValue("t2", t[1]).addValue("t3", t[2]);

        StringBuilder where = new StringBuilder("""
                WHERE e.status = 'ACTIVE' AND u.account_status = 'ACTIVE' AND u.category_verification = 'VERIFIED'
                """);
        if (!adult) {
            where.append(" AND e.primary_type NOT IN (:alcohol)");
            p.addValue("alcohol", Arrays.stream(EstablishmentType.values())
                    .filter(EstablishmentType::isAlcoholFocused).map(Enum::name).toList());
        }
        if (text != null) {
            where.append(" AND lower(f_unaccent(e.name)) LIKE '%' || lower(f_unaccent(:q)) || '%'");
            p.addValue("q", text.replace("\\", "").replace("%", "").replace("_", ""));
        }
        anyOf(where, p, "e.cuisines_all", "c", cuisines);
        anyOf(where, p, "e.formats_all", "f", formats);
        if (!types.isEmpty()) {
            where.append(" AND (e.primary_type IN (:types)");
            int i = 0;
            for (String type : types) {
                where.append(" OR e.secondary_types @> CAST(:ty").append(i).append(" AS jsonb)");
                p.addValue("ty" + i++, "[\"" + type + "\"]");
            }
            where.append(")");
            p.addValue("types", List.copyOf(types));
        }
        if (!groups.isEmpty()) {
            where.append(" AND e.type_group IN (:groups)");
            p.addValue("groups", List.copyOf(groups));
        }
        int d = 0;
        for (String style : dietary) {
            where.append(" AND jsonb_exists(e.dietary_offers, :d").append(d).append(")");
            p.addValue("d" + d++, style);
        }
        if (q.municipality() != null) {
            where.append(" AND e.municipality_ibge = :municipality");
            p.addValue("municipality", q.municipality());
        }
        if (geo) {
            where.append("""
                     AND e.address_visibility <> 'ADDR_HIDDEN' AND e.point IS NOT NULL
                     AND ST_DWithin(e.point, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :prefilter)
                    """);
            p.addValue("lat", q.lat()).addValue("lng", q.lng())
                    .addValue("prefilter", radius * 1000 + PREFILTER_SLACK_M).addValue("radiusM", radius * 1000);
        }

        // ponto público: arredondado na grade de 0,005° quando o endereço é aproximado (igual a publicCoordinate)
        String approx = "(e.address_visibility <> 'ADDR_PUBLIC_FULL' OR u.type_details->>'informalFormat' = 'COZINHA_CASEIRA')";
        String distance = geo ? """
                ST_Distance(ST_SetSRID(ST_MakePoint(
                    CASE WHEN %1$s THEN floor(e.longitude / 0.005) * 0.005 + 0.0025 ELSE e.longitude END,
                    CASE WHEN %1$s THEN floor(e.latitude / 0.005) * 0.005 + 0.0025 ELSE e.latitude END), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography)""".formatted(approx) : "NULL::double precision";
        // peso igual ao do RatingCalculator: confiança × max(piso, 0,5^(idade em dias / meia-vida)), idade em horas inteiras
        String sql = """
                WITH base AS (
                    SELECT e.id, %2$s AS approximate, %3$s AS distance_m, r.n, r.swr, r.sw,
                           CASE WHEN r.n >= :minDisplay AND r.sw > 0 THEN round((r.swr / r.sw)::numeric, 1) END AS shown,
                           (:priorWeight * :priorMean + COALESCE(r.swr, 0)) / (:priorWeight + COALESCE(r.sw, 0)) AS score,
                           CASE WHEN e.price_not_applicable THEN 'PRICE_NA'
                                WHEN pr.reports >= :minReports THEN %4$s
                                WHEN e.price_min_cents IS NOT NULL THEN %5$s
                                ELSE 'PRICE_UNKNOWN' END AS price_level
                    FROM establishment e
                    JOIN user_account u ON u.id = e.owner_id
                    LEFT JOIN LATERAL (
                        SELECT count(*) AS n, sum(w * rv.rating) AS swr, sum(w) AS sw
                        FROM (SELECT x.rating,
                                     (CASE x.trust WHEN 'REVIEW_UNVERIFIED' THEN 0.6 WHEN 'REVIEW_INCENTIVIZED' THEN 0.6
                                                   WHEN 'REVIEW_CONFLICT' THEN 0.0 ELSE 1.0 END)
                                     * greatest(:floor, power(0.5, floor(greatest(0, extract(epoch FROM now() - x.updated_at))
                                                                      / 3600) / 24.0 / :halfLife)) AS w
                              FROM review x JOIN user_account a ON a.id = x.author_id
                              WHERE x.establishment_id = e.id AND x.status = 'PUBLISHED' AND a.account_status = 'ACTIVE') rv
                    ) r ON TRUE
                    LEFT JOIN LATERAL (
                        SELECT count(*) AS reports, percentile_cont(0.5) WITHIN GROUP (ORDER BY x.spent_per_person_cents) AS median
                        FROM review x JOIN user_account a ON a.id = x.author_id
                        WHERE x.establishment_id = e.id AND x.status = 'PUBLISHED' AND a.account_status = 'ACTIVE'
                          AND x.spent_per_person_cents IS NOT NULL AND x.updated_at > now() - interval '365 days'
                    ) pr ON TRUE
                    %1$s
                )
                SELECT id, distance_m, approximate, count(*) OVER () AS total FROM base
                WHERE TRUE %6$s
                ORDER BY %7$s
                LIMIT :limit OFFSET :offset
                """.formatted(where, approx, distance, level("pr.median"),
                level("CASE WHEN e.price_max_cents IS NULL THEN e.price_min_cents "
                        + "ELSE floor((e.price_min_cents + e.price_max_cents) / 2) END"),
                filters(geo, q.minRating(), priceLevels, p), order(sort, geo));

        List<Object[]> rows = jdbc.query(sql, p, (rs, i) -> new Object[]{rs.getLong("id"),
                rs.getObject("distance_m") == null ? null : rs.getDouble("distance_m"),
                rs.getBoolean("approximate"), rs.getLong("total")});
        long total = rows.isEmpty() ? 0 : (long) rows.getFirst()[3];
        if (rows.isEmpty() && page > 0) {
            total = jdbc.queryForObject(sql.replaceFirst("(?s)SELECT id, distance_m.*", "SELECT count(*) FROM base WHERE TRUE "
                    + filters(geo, q.minRating(), priceLevels, p)), p, Long.class);
        }
        return new Page(cards(rows), page, size, total, geo ? radius : null);
    }

    /** Nível pelos limites da escala ({@code [início, fim)}), a partir de uma expressão em centavos. */
    private static String level(String cents) {
        return "CASE WHEN (%1$s) < :t1 THEN 'PRICE_1' WHEN (%1$s) < :t2 THEN 'PRICE_2' WHEN (%1$s) < :t3 THEN 'PRICE_3' "
                .formatted(cents) + "ELSE 'PRICE_4' END";
    }

    private static String filters(boolean geo, Double minRating, Set<String> priceLevels, MapSqlParameterSource p) {
        StringBuilder f = new StringBuilder();
        if (geo) {
            f.append(" AND distance_m <= :radiusM");
        }
        if (minRating != null) {
            f.append(" AND shown >= :minRating");
            p.addValue("minRating", minRating);
        }
        if (!priceLevels.isEmpty()) {
            f.append(" AND price_level IN (:priceLevels)");
            p.addValue("priceLevels", List.copyOf(priceLevels));
        }
        return f.toString();
    }

    private static String order(Sort sort, boolean geo) {
        return switch (sort) {
            case DISTANCE -> "distance_m ASC, id ASC";
            case RATING -> "shown DESC NULLS LAST, n DESC, id ASC";
            case RELEVANCE -> geo
                    ? "score - " + DISTANCE_PENALTY + " * ln(1 + distance_m / 1000.0) DESC, id ASC"
                    : "score DESC, id ASC";
        };
    }

    /** Uma das culinárias/um dos formatos: OR de {@code @>} para o índice GIN jsonb_path_ops servir. */
    private static void anyOf(StringBuilder where, MapSqlParameterSource p, String column, String prefix, Set<String> codes) {
        if (codes.isEmpty()) {
            return;
        }
        where.append(" AND (");
        int i = 0;
        for (String code : codes) {
            if (i > 0) where.append(" OR ");
            where.append(column).append(" @> CAST(:").append(prefix).append(i).append(" AS jsonb)");
            p.addValue(prefix + i++, "[\"" + code + "\"]");
        }
        where.append(")");
    }

    /** BRASILEIRA também traz MINEIRA, BAIANA…; NORDESTINA traz BAIANA. */
    static Set<String> expandCuisines(Set<String> codes) {
        Set<String> out = new LinkedHashSet<>(codes);
        for (Cuisine c : Cuisine.values()) {
            for (String code : codes) {
                if (Cuisine.valueOf(code).isAncestorOf(c)) {
                    out.add(c.name());
                }
            }
        }
        return out;
    }

    private static <T> Set<String> codes(List<String> raw, Function<String, Optional<T>> lookup, String field,
                                         List<String> errors) {
        Set<String> out = new LinkedHashSet<>();
        if (raw == null) {
            return out;
        }
        for (String code : raw) {
            for (String c : code.split(",")) {
                String s = c.strip();
                if (s.isEmpty()) continue;
                if (lookup.apply(s).isEmpty()) {
                    errors.add("Código desconhecido em \"" + field + "\": " + s + " (veja GET /api/v1/taxonomy).");
                } else {
                    out.add(s);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------ cards

    private List<Card> cards(List<Object[]> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<Long> ids = rows.stream().map(r -> (Long) r[0]).toList();
        Map<Long, Establishment> byId = establishments.findAllById(ids).stream()
                .collect(Collectors.toMap(Establishment::getId, Function.identity()));
        Map<Long, UserAccount> owners = users.findAllById(byId.values().stream().map(Establishment::getOwnerId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(UserAccount::getId, Function.identity()));
        Map<Long, List<Review>> counting = reviews.findCountingIn(ids, AccountStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(Review::getEstablishmentId));
        List<Card> out = new ArrayList<>();
        for (Object[] row : rows) {
            Establishment e = byId.get((Long) row[0]);
            List<Review> rs = counting.getOrDefault(e.getId(), List.of());
            EstablishmentViews.LocationView l = EstablishmentService.location(e, false,
                    Optional.ofNullable(owners.get(e.getOwnerId())));
            EstablishmentViews.ClassificationView c = EstablishmentService.classification(e);
            out.add(new Card(e.getId(), e.getName(), EstablishmentService.type(e.getPrimaryType()),
                    new Coded(e.getTypeGroup().name(), e.getTypeGroup().getLabel(), null),
                    ChipView.of(CuisineChip.of(e.getPrimaryCuisine(), e.getPrimaryFormat(), e.getDietaryOffers())),
                    c.primaryCuisine(), c.primaryFormat(), pages.price(e, rs), pages.rating(rs),
                    new CardLocation(l.uf(), l.city(), l.neighborhood(), l.approximate()),
                    distanceKm((Double) row[1], (Boolean) row[2]), e.isAlcoholFocused(), e.getStatus()));
        }
        return out;
    }

    /** Em km com uma casa; endereço aproximado vai de 0,5 em 0,5 km (nunca 0). */
    static Double distanceKm(Double meters, boolean approximate) {
        if (meters == null) {
            return null;
        }
        double km = meters / 1000;
        return approximate ? Math.max(0.5, Math.round(km * 2) / 2.0) : Math.round(km * 10) / 10.0;
    }
}
