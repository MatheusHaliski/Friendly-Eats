package com.friendlyeats.application.establishment;

import com.friendlyeats.application.establishment.EstablishmentDrafts.ClassificationDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.LocationDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.PriceDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.ReviewDraft;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.AddressVisibility;
import com.friendlyeats.domain.taxonomy.Approach;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.EstablishmentGroup;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.GeoPrecision;
import com.friendlyeats.domain.taxonomy.ReviewDimension;
import com.friendlyeats.domain.taxonomy.Uf;
import com.friendlyeats.domain.taxonomy.VisitMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Regras da taxonomia aplicadas ao cadastro (RF6), à classificação (RF7) e à avaliação (RF8). Puras: cada método
 * acrescenta os problemas em {@code errors}, para a API devolver todos juntos (422), e devolve o valor normalizado.
 */
public final class EstablishmentRules {

    static final int MAX_SECONDARY_TYPES = 3;
    static final int MAX_SECONDARY_CUISINES = 2;
    static final int MAX_SECONDARY_FORMATS = 4;
    static final int MAX_PRICE_CENTS = 500_000;
    static final int MAX_REVIEW_BODY = 2000;
    /** Grade do ponto público quando o endereço é a casa do dono: 0,005° ≈ 550 m. */
    static final double PUBLIC_GRID_DEGREES = 0.005;

    /** Tipos que um negócio com CPF pode ter, pelo {@code informalFormat} do cadastro (RF1). */
    static final Map<String, Set<EstablishmentType>> INFORMAL_TYPES = Map.of(
            "AMBULANTE", Set.of(EstablishmentType.AMBULANTE),
            "FOOD_TRUCK", Set.of(EstablishmentType.FOOD_TRUCK),
            "COZINHA_CASEIRA", Set.of(EstablishmentType.COZINHA_ARTESANAL, EstablishmentType.MARMITARIA));

    private EstablishmentRules() {
    }

    public record Types(EstablishmentType primary, List<EstablishmentType> secondary) {
    }

    public record Classification(Cuisine primaryCuisine, List<Cuisine> secondaryCuisines, FoodFormat primaryFormat,
                                 List<FoodFormat> secondaryFormats, Approach approach,
                                 Map<DietaryStyle, DietaryStyle.Level> dietaryOffers) {
    }

    public record Location(String uf, String municipalityIbge, String city, String neighborhood, String street,
                           String streetNumber, String complement, String cep, Double latitude, Double longitude,
                           GeoPrecision geoPrecision, AddressVisibility visibility) {
    }

    public record Price(Integer minCents, Integer maxCents, boolean notApplicable) {
    }

    public record ReviewContent(int rating, VisitMode visitMode, Map<ReviewDimension, Integer> dimensions,
                                Integer spentPerPersonCents, String body) {
    }

    // ------------------------------------------------------------ tipo (RF6)

    /**
     * 1 principal + até 3 secundários distintos (R1). Conta VAREJO cadastra tipo principal do grupo VAREJO; conta
     * ESTABELECIMENTO, dos demais. POLO só é secundário de outro POLO (R4). Negócio com CPF fica nos tipos do seu
     * formato informal.
     */
    public static Types types(UserCategoryEnum ownerType, String informalFormat, String primaryCode,
                              List<String> secondaryCodes, List<String> errors) {
        EstablishmentType primary = parse(primaryCode, EstablishmentType::fromCode, "primaryType", errors, true);
        List<EstablishmentType> secondary = parseList(secondaryCodes, EstablishmentType::fromCode, "secondaryTypes",
                MAX_SECONDARY_TYPES, "tipos secundários", errors);
        if (primary == null) {
            return null;
        }
        if (secondary.contains(primary)) {
            errors.add("O tipo principal não pode se repetir entre os secundários.");
        }
        if (ownerType == UserCategoryEnum.VAREJO && primary.getGroup() != EstablishmentGroup.VAREJO) {
            errors.add("Conta de Varejo & Marca cadastra loja do grupo Varejo (mercado, empório, adega…).");
        }
        if (ownerType == UserCategoryEnum.ESTABELECIMENTO && primary.getGroup() == EstablishmentGroup.VAREJO) {
            errors.add("Loja de varejo é cadastrada por uma conta de Varejo & Marca.");
        }
        if (primary.getGroup() != EstablishmentGroup.POLO
                && secondary.stream().anyMatch(t -> t.getGroup() == EstablishmentGroup.POLO)) {
            errors.add("Mercado, feira ou praça de alimentação só entra como tipo principal: um lugar dentro deles "
                    + "é outro estabelecimento.");
        }
        if (informalFormat != null) {
            Set<EstablishmentType> allowed = INFORMAL_TYPES.getOrDefault(informalFormat, Set.of());
            if (!allowed.contains(primary) || !allowed.containsAll(secondary)) {
                errors.add("Cadastro com CPF (" + informalFormat + ") aceita só o tipo "
                        + String.join(" ou ", allowed.stream().map(Enum::name).sorted().toList()) + ".");
            }
        }
        return new Types(primary, secondary);
    }

    // ------------------------------------------------------------ classificação (RF7)

    /**
     * Culinária 1 + até 2 e formato 1 + até 4, nunca uma culinária junto com um ancestral dela (BAIANA já é
     * NORDESTINA e BRASILEIRA). Precisa de culinária ou formato, salvo varejo, polo e abordagem VARIADA.
     */
    public static Classification classification(EstablishmentGroup group, ClassificationDraft d, List<String> errors) {
        ClassificationDraft draft = d == null ? new ClassificationDraft(null, null, null, null, null, null) : d;
        Cuisine primaryCuisine = parse(draft.primaryCuisine(), Cuisine::fromCode, "primaryCuisine", errors, false);
        List<Cuisine> secondaryCuisines = parseList(draft.secondaryCuisines(), Cuisine::fromCode, "secondaryCuisines",
                MAX_SECONDARY_CUISINES, "culinárias secundárias", errors);
        FoodFormat primaryFormat = parse(draft.primaryFormat(), FoodFormat::fromCode, "primaryFormat", errors, false);
        List<FoodFormat> secondaryFormats = parseList(draft.secondaryFormats(), FoodFormat::fromCode,
                "secondaryFormats", MAX_SECONDARY_FORMATS, "formatos secundários", errors);
        Approach approach = draft.approach() == null || draft.approach().isBlank() ? Approach.TRADICIONAL
                : parse(draft.approach(), c -> Arrays.stream(Approach.values()).filter(a -> a.name().equals(c))
                .findFirst(), "approach", errors, false);

        if (primaryCuisine == null && !secondaryCuisines.isEmpty()) {
            errors.add("Escolha a culinária principal antes das secundárias.");
        }
        if (primaryFormat == null && !secondaryFormats.isEmpty()) {
            errors.add("Escolha o formato principal antes dos secundários.");
        }
        if (primaryCuisine != null && secondaryCuisines.contains(primaryCuisine)) {
            errors.add("A culinária principal não pode se repetir entre as secundárias.");
        }
        if (primaryFormat != null && secondaryFormats.contains(primaryFormat)) {
            errors.add("O formato principal não pode se repetir entre os secundários.");
        }
        List<Cuisine> all = new ArrayList<>();
        if (primaryCuisine != null) all.add(primaryCuisine);
        all.addAll(secondaryCuisines);
        for (Cuisine a : all) {
            for (Cuisine b : all) {
                if (a.isAncestorOf(b)) {
                    errors.add(b.getLabel() + " já inclui " + a.getLabel() + ": deixe só " + b.getLabel() + ".");
                }
            }
        }
        boolean optional = group == EstablishmentGroup.VAREJO || group == EstablishmentGroup.POLO
                || approach == Approach.VARIADA;
        if (!optional && primaryCuisine == null && primaryFormat == null) {
            errors.add("Informe ao menos a culinária ou o tipo de comida principal.");
        }
        if (approach == Approach.CONTEMPORANEA && primaryCuisine == null) {
            errors.add("Cozinha contemporânea precisa da culinária de base (ex.: Brasileira contemporânea).");
        }
        if (approach == Approach.FUSION && all.size() < 2 && (primaryCuisine == null || !primaryCuisine.isMacroGeneric())) {
            errors.add("Fusion junta tradições: informe 2 culinárias ou uma genérica (ex.: Asiática para asian fusion).");
        }

        Map<DietaryStyle, DietaryStyle.Level> offers = new EnumMap<>(DietaryStyle.class);
        if (draft.dietaryOffers() != null) {
            draft.dietaryOffers().forEach((k, v) -> {
                Optional<DietaryStyle> style = DietaryStyle.fromCode(k);
                Optional<DietaryStyle.Level> level = Arrays.stream(DietaryStyle.Level.values())
                        .filter(l -> l.name().equals(v)).findFirst();
                if (style.isEmpty()) {
                    errors.add("Estilo alimentar desconhecido: \"" + k + "\".");
                } else if (level.isEmpty()) {
                    errors.add("Nível de \"" + k + "\": use OFERECE ou EXCLUSIVO.");
                } else {
                    offers.put(style.get(), level.get());
                }
            });
        }
        return new Classification(primaryCuisine, secondaryCuisines, primaryFormat, secondaryFormats,
                approach == null ? Approach.TRADICIONAL : approach, offers);
    }

    // ------------------------------------------------------------ local (RF6)

    /**
     * Município pelo código IBGE (7 dígitos, começando pela UF); ponto dentro do Brasil. Cozinha caseira com CPF
     * mostra só o bairro: o endereço é a casa do dono.
     */
    public static Location location(LocationDraft d, boolean homeKitchen, List<String> errors) {
        if (d == null) {
            errors.add("Informe o local: UF, município (código IBGE) e cidade.");
            return null;
        }
        Optional<Uf> uf = Uf.fromCode(upper(d.uf()));
        if (uf.isEmpty()) {
            errors.add("UF inválida: use a sigla, ex.: SP.");
        }
        String municipality = strip(d.municipalityIbge());
        if (municipality == null || !municipality.matches("^\\d{7}$")) {
            errors.add("Município pelo código IBGE de 7 dígitos, ex.: 3550308 (São Paulo).");
        } else if (uf.isPresent() && !uf.get().ownsMunicipality(municipality)) {
            errors.add("O código IBGE " + municipality + " não é de um município de " + uf.get().name() + ".");
        }
        String city = text(d.city(), "city", 120, errors);
        if (city == null) {
            errors.add("Informe o nome da cidade.");
        }
        String neighborhood = text(d.neighborhood(), "neighborhood", 120, errors);
        String street = text(d.street(), "street", 160, errors);
        String number = text(d.streetNumber(), "streetNumber", 20, errors);
        String complement = text(d.complement(), "complement", 80, errors);
        String cep = d.cep() == null ? null : d.cep().replaceAll("[\\s.-]", "");
        if (cep != null && cep.isEmpty()) cep = null;
        if (cep != null && !cep.matches("^\\d{8}$")) {
            errors.add("CEP com 8 dígitos.");
        }
        if ((d.latitude() == null) != (d.longitude() == null)) {
            errors.add("Informe latitude e longitude juntas (o pino no mapa).");
        } else if (d.latitude() != null && !insideBrazil(d.latitude(), d.longitude())) {
            errors.add("O pino do mapa está fora do Brasil.");
        }
        AddressVisibility visibility = d.addressVisibility() == null || d.addressVisibility().isBlank()
                ? (homeKitchen ? AddressVisibility.ADDR_PUBLIC_BAIRRO : AddressVisibility.ADDR_PUBLIC_FULL)
                : parse(d.addressVisibility(), c -> Arrays.stream(AddressVisibility.values())
                .filter(v -> v.name().equals(c)).findFirst(), "addressVisibility", errors, false);
        if (homeKitchen && visibility == AddressVisibility.ADDR_PUBLIC_FULL) {
            errors.add("Cozinha caseira mostra só o bairro: o endereço é a sua casa.");
        }
        if (visibility == AddressVisibility.ADDR_PUBLIC_FULL && street == null) {
            errors.add("Para mostrar o endereço completo, informe a rua (ou mostre só o bairro).");
        }
        GeoPrecision precision = d.latitude() != null ? GeoPrecision.GEO_PIN_CONFIRMED : GeoPrecision.GEO_MUNICIPIO_CENTROID;
        return new Location(uf.map(Enum::name).orElse(null), municipality, city, neighborhood, street, number,
                complement, cep, d.latitude(), d.longitude(), precision, visibility);
    }

    static boolean insideBrazil(double lat, double lng) {
        return lat >= -34.0 && lat <= 5.5 && lng >= -74.1 && lng <= -28.6;
    }

    /** Ponto público quando só o bairro é mostrado: centro da célula de 0,005°, igual para todo mundo. */
    public static double publicCoordinate(double v) {
        return Math.round(Math.floor(v / PUBLIC_GRID_DEGREES) * PUBLIC_GRID_DEGREES * 1e4 + PUBLIC_GRID_DEGREES / 2 * 1e4) / 1e4;
    }

    // ------------------------------------------------------------ preço (RF6)

    public static Price price(EstablishmentGroup group, PriceDraft d, List<String> errors) {
        if (d == null) {
            return new Price(null, null, false);
        }
        boolean na = Boolean.TRUE.equals(d.notApplicable());
        if (na) {
            if (group != EstablishmentGroup.VAREJO && group != EstablishmentGroup.POLO) {
                errors.add("\"Não se aplica\" é só para varejo sem refeição; informe o gasto por pessoa.");
            }
            if (d.minCents() != null || d.maxCents() != null) {
                errors.add("Com \"não se aplica\", não informe valores.");
            }
            return new Price(null, null, true);
        }
        if (d.maxCents() != null && d.minCents() == null) {
            errors.add("Informe também o gasto mínimo por pessoa.");
        }
        for (Integer v : new Integer[]{d.minCents(), d.maxCents()}) {
            if (v != null && (v < 0 || v > MAX_PRICE_CENTS)) {
                errors.add("Gasto por pessoa entre R$ 0 e R$ 5.000.");
                break;
            }
        }
        if (d.minCents() != null && d.maxCents() != null && d.maxCents() < d.minCents()) {
            errors.add("O gasto máximo por pessoa é menor que o mínimo.");
        }
        return new Price(d.minCents(), d.maxCents(), false);
    }

    // ------------------------------------------------------------ avaliação (RF8)

    /** Nota inteira de 1 a 5 (sem 0, sem meia estrela); cada dimensão só no modo em que faz sentido. */
    public static ReviewContent review(ReviewDraft d, List<String> errors) {
        if (d == null) {
            errors.add("Dê uma nota de 1 a 5.");
            return null;
        }
        if (d.rating() == null || d.rating() < 1 || d.rating() > 5) {
            errors.add("Dê uma nota inteira de 1 (Péssimo) a 5 (Excelente).");
        }
        VisitMode mode = parse(d.visitMode(), c -> Arrays.stream(VisitMode.values()).filter(v -> v.name().equals(c))
                .findFirst(), "visitMode", errors, true);
        Map<ReviewDimension, Integer> dims = new EnumMap<>(ReviewDimension.class);
        if (d.dimensions() != null) {
            d.dimensions().forEach((k, v) -> {
                Optional<ReviewDimension> dim = Arrays.stream(ReviewDimension.values())
                        .filter(x -> x.name().equals(k)).findFirst();
                if (dim.isEmpty()) {
                    errors.add("Dimensão desconhecida: \"" + k + "\".");
                } else if (v == null || v < 1 || v > 5) {
                    errors.add(dim.get().getLabel() + ": nota inteira de 1 a 5.");
                } else if (mode != null && !dim.get().appliesTo(mode)) {
                    errors.add(dim.get().getLabel() + " não se avalia em \"" + mode.getLabel() + "\".");
                } else {
                    dims.put(dim.get(), v);
                }
            });
        }
        Integer spent = d.spentPerPersonCents();
        if (spent != null && (spent < 0 || spent > MAX_PRICE_CENTS)) {
            errors.add("Gasto por pessoa entre R$ 0 e R$ 5.000.");
        }
        String body = text(d.body(), "body", MAX_REVIEW_BODY, errors);
        return d.rating() == null ? null : new ReviewContent(d.rating(), mode, dims, spent, body);
    }

    // ------------------------------------------------------------ apoio

    static <T> T parse(String code, Function<String, Optional<T>> lookup, String field, List<String> errors,
                       boolean required) {
        if (code == null || code.isBlank()) {
            if (required) {
                errors.add("Preencha \"" + field + "\".");
            }
            return null;
        }
        Optional<T> v = lookup.apply(code.strip());
        if (v.isEmpty()) {
            errors.add("Código desconhecido em \"" + field + "\": " + code + " (veja GET /api/v1/taxonomy).");
        }
        return v.orElse(null);
    }

    static <T> List<T> parseList(List<String> codes, Function<String, Optional<T>> lookup, String field, int max,
                                 String what, List<String> errors) {
        if (codes == null) {
            return List.of();
        }
        Set<T> out = new LinkedHashSet<>();
        for (String c : codes) {
            T v = parse(c, lookup, field, errors, true);
            if (v != null && !out.add(v)) {
                errors.add("Código repetido em \"" + field + "\": " + c + ".");
            }
        }
        if (out.size() > max) {
            errors.add("No máximo " + max + " " + what + ".");
        }
        return List.copyOf(out);
    }

    static String text(String v, String field, int max, List<String> errors) {
        String s = strip(v);
        if (s != null && s.length() > max) {
            errors.add("\"" + field + "\" passa de " + max + " caracteres.");
        }
        return s;
    }

    private static String strip(String v) {
        return v == null || v.isBlank() ? null : v.strip();
    }

    private static String upper(String v) {
        return v == null ? null : v.strip().toUpperCase(java.util.Locale.ROOT);
    }
}
