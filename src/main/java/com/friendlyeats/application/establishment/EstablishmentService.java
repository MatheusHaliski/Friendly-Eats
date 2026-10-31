package com.friendlyeats.application.establishment;

import com.friendlyeats.application.account.AccountViews.CategoryView;
import com.friendlyeats.application.age.AgeGate;
import com.friendlyeats.application.common.CommonExceptions;
import com.friendlyeats.application.establishment.EstablishmentDrafts.ClassificationDraft;
import com.friendlyeats.application.establishment.EstablishmentDrafts.EstablishmentDraft;
import com.friendlyeats.application.establishment.EstablishmentRules.Classification;
import com.friendlyeats.application.establishment.EstablishmentRules.Location;
import com.friendlyeats.application.establishment.EstablishmentRules.Price;
import com.friendlyeats.application.establishment.EstablishmentRules.Types;
import com.friendlyeats.application.establishment.EstablishmentViews.ChipView;
import com.friendlyeats.application.establishment.EstablishmentViews.ClassificationView;
import com.friendlyeats.application.establishment.EstablishmentViews.Coded;
import com.friendlyeats.application.establishment.EstablishmentViews.DietaryOfferView;
import com.friendlyeats.application.establishment.EstablishmentViews.EstablishmentView;
import com.friendlyeats.application.establishment.EstablishmentViews.LocationView;
import com.friendlyeats.application.establishment.EstablishmentViews.OwnerView;
import com.friendlyeats.application.establishment.EstablishmentViews.PriceView;
import com.friendlyeats.application.establishment.EstablishmentViews.RatingView;
import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.Establishment;
import com.friendlyeats.domain.model.Review;
import com.friendlyeats.domain.model.UserAccount;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.taxonomy.AddressVisibility;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.CuisineChip;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.PriceLevel;
import com.friendlyeats.infrastructure.persistence.postgres.EstablishmentRepository;
import com.friendlyeats.infrastructure.persistence.postgres.ReviewRepository;
import com.friendlyeats.infrastructure.persistence.postgres.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Cadastro (RF6) e classificação (RF7) do estabelecimento, e a página com nota e faixa de preço derivadas (RF8).
 *
 * <ul>
 *   <li>Só conta ESTABELECIMENTO ou VAREJO, ativa, é dona de lugar; negócio com CPF tem um único ponto.</li>
 *   <li>A página só é pública com o selo da conta dona conferido ({@code VERIFIED}) e a conta ativa; antes disso o
 *       dono vê tudo e os motivos ({@code publishBlockers}).</li>
 *   <li>Lugar com foco em bebida alcoólica pede login e só aparece para maior de 18 com a idade verificada (RF17,
 *       {@link AgeGate}).</li>
 * </ul>
 */
@Service
public class EstablishmentService {

    private static final Duration PRICE_REPORT_WINDOW = Duration.ofDays(365);

    private final EstablishmentRepository establishments;
    private final ReviewRepository reviews;
    private final UserAccountRepository users;
    private final PriceScale priceScale;
    private final RatingCalculator ratings;
    private final AgeGate ageGate;
    private final Clock clock;
    private final int minPriceReports;

    public EstablishmentService(EstablishmentRepository establishments, ReviewRepository reviews,
                                UserAccountRepository users, PriceScale priceScale, RatingCalculator ratings,
                                AgeGate ageGate, Clock clock,
                                @org.springframework.beans.factory.annotation.Value("${friendly-eats.price.min-reports:5}")
                                int minPriceReports) {
        this.establishments = establishments;
        this.reviews = reviews;
        this.users = users;
        this.priceScale = priceScale;
        this.ratings = ratings;
        this.ageGate = ageGate;
        this.clock = clock;
        this.minPriceReports = minPriceReports;
    }

    // ------------------------------------------------------------ cadastro e edição (RF6, RF7)

    @Transactional
    public EstablishmentView create(long viewerId, EstablishmentDraft d) {
        UserAccount owner = requireOwnerAccount(viewerId);
        String informal = informalFormat(owner);
        if (informal != null) {
            establishments.lockOwner(viewerId);
        }
        if (informal != null && establishments.countByOwnerId(viewerId) > 0) {
            throw new CommonExceptions.Conflict("Cadastro com CPF tem um único ponto de venda. Para abrir outro, "
                    + "cadastre o negócio com CNPJ.");
        }
        List<String> errors = new ArrayList<>();
        String name = EstablishmentRules.text(d.name(), "name", 120, errors);
        if (name == null) {
            errors.add("Informe o nome do lugar.");
        }
        String description = EstablishmentRules.text(d.description(), "description", 500, errors);
        Types types = EstablishmentRules.types(owner.getUserType(), informal, d.primaryType(), d.secondaryTypes(), errors);
        Location location = EstablishmentRules.location(d.location(), "COZINHA_CASEIRA".equals(informal), errors);
        Price price = types == null ? null : EstablishmentRules.price(types.primary().getGroup(), d.price(), errors);
        Classification classification = types == null ? null
                : EstablishmentRules.classification(types.primary().getGroup(), d.classification(), errors);
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        Establishment e = new Establishment(viewerId, name);
        e.setDescription(description);
        e.setTypes(types.primary(), types.secondary());
        apply(e, location);
        e.setDeclaredPrice(price.minCents(), price.maxCents(), price.notApplicable());
        apply(e, classification);
        return view(establishments.save(e), Optional.of(owner));
    }

    /** PATCH: campo nulo não mexe; tipos, local e preço trocam por inteiro. Dono pode marcar CLOSED e reabrir. */
    @Transactional
    public EstablishmentView update(long viewerId, long id, EstablishmentDraft d) {
        UserAccount owner = requireOwnerAccount(viewerId);
        Establishment e = requireOwned(id, viewerId);
        String informal = informalFormat(owner);
        List<String> errors = new ArrayList<>();
        if (d.name() != null) {
            String name = EstablishmentRules.text(d.name(), "name", 120, errors);
            if (name == null) errors.add("O nome não pode ficar vazio.");
            else e.setName(name);
        }
        if (d.description() != null) {
            e.setDescription(EstablishmentRules.text(d.description(), "description", 500, errors));
        }
        if (d.primaryType() != null || d.secondaryTypes() != null) {
            Types t = EstablishmentRules.types(owner.getUserType(), informal,
                    d.primaryType() == null ? e.getPrimaryType().name() : d.primaryType(),
                    d.secondaryTypes() == null ? e.getSecondaryTypes().stream().map(Enum::name).toList() : d.secondaryTypes(),
                    errors);
            if (t != null && errors.isEmpty()) {
                e.setTypes(t.primary(), t.secondary());
                // a classificação atual precisa continuar válida no grupo novo (restaurante exige culinária ou formato)
                if (d.classification() == null) {
                    EstablishmentRules.classification(e.getTypeGroup(), draftOf(e), errors);
                }
                if (d.price() == null) {     // "não se aplica" só vale para varejo e polo
                    EstablishmentRules.price(e.getTypeGroup(), new EstablishmentDrafts.PriceDraft(e.getPriceMinCents(),
                            e.getPriceMaxCents(), e.isPriceNotApplicable()), errors);
                }
            }
        }
        if (d.location() != null) {
            Location l = EstablishmentRules.location(d.location(), "COZINHA_CASEIRA".equals(informal), errors);
            if (errors.isEmpty()) apply(e, l);
        }
        if (d.price() != null) {
            Price p = EstablishmentRules.price(e.getTypeGroup(), d.price(), errors);
            if (errors.isEmpty()) e.setDeclaredPrice(p.minCents(), p.maxCents(), p.notApplicable());
        }
        if (d.classification() != null) {
            Classification c = EstablishmentRules.classification(e.getTypeGroup(), d.classification(), errors);
            if (errors.isEmpty()) apply(e, c);
        }
        if (d.status() != null) {
            switch (d.status()) {
                case "ACTIVE", "CLOSED" -> {
                    if (e.getStatus() == Establishment.Status.REMOVED) {
                        throw new CommonExceptions.Conflict("Lugar removido pela moderação; peça revisão ao suporte.");
                    }
                    e.setStatus(Establishment.Status.valueOf(d.status()));
                }
                default -> errors.add("Status: use ACTIVE (aberto) ou CLOSED (fechou de vez).");
            }
        }
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        return view(establishments.save(e), Optional.of(owner));
    }

    /** PUT da classificação inteira (RF7). */
    @Transactional
    public EstablishmentView classify(long viewerId, long id, ClassificationDraft d) {
        UserAccount owner = requireOwnerAccount(viewerId);
        Establishment e = requireOwned(id, viewerId);
        List<String> errors = new ArrayList<>();
        Classification c = EstablishmentRules.classification(e.getTypeGroup(), d, errors);
        if (!errors.isEmpty()) {
            throw new CommonExceptions.Invalid(errors);
        }
        apply(e, c);
        return view(establishments.save(e), Optional.of(owner));
    }

    @Transactional(readOnly = true)
    public List<EstablishmentView> mine(long viewerId) {
        Optional<UserAccount> owner = users.findById(viewerId);
        return establishments.findByOwnerIdOrderByCreatedAtAsc(viewerId).stream().map(e -> view(e, owner)).toList();
    }

    @Transactional(readOnly = true)
    public EstablishmentView get(Optional<Long> viewerId, long id) {
        Optional<UserAccount> viewer = viewerId.flatMap(users::findById);
        return view(requireVisible(id, viewer), viewer);
    }

    // ------------------------------------------------------------ quem vê o quê

    /**
     * Página visível? O dono sempre vê a sua (inclusive fora do ar). Os demais só com status ACTIVE/CLOSED, conta
     * dona ativa e selo conferido; com foco em bebida alcoólica, só logado e com 18+ verificado (RF17). Não revela qual regra barrou.
     */
    @Transactional(readOnly = true)
    public Establishment requireVisible(long id, Optional<UserAccount> viewer) {
        Establishment e = establishments.findById(id).orElseThrow(this::notFound);
        if (viewer.isPresent() && Objects.equals(viewer.get().getId(), e.getOwnerId())) {
            return e;
        }
        if (!publishBlockers(e, users.findById(e.getOwnerId())).isEmpty()) {
            throw notFound();
        }
        if (e.isAlcoholFocused()) {
            ageGate.requireAlcoholAccess(viewer.orElse(null), this::notFound);
        }
        return e;
    }

    private List<String> publishBlockers(Establishment e, Optional<UserAccount> owner) {
        List<String> out = new ArrayList<>();
        if (e.getStatus() == Establishment.Status.REMOVED) {
            out.add("Removido pela moderação.");
        }
        if (owner.isEmpty() || owner.get().getAccountStatus() != AccountStatus.ACTIVE) {
            out.add("A conta dona não está ativa.");
        } else if (owner.get().getCategoryVerification() != CategoryVerification.VERIFIED) {
            out.add(owner.get().getCategoryVerification() == CategoryVerification.REJECTED
                    ? "O selo da conta foi recusado: revise os dados do cadastro."
                    : "Aguardando a conferência do selo da conta (CNPJ ou CPF e documentos).");
        }
        return out;
    }

    private UserAccount requireOwnerAccount(long viewerId) {
        UserAccount u = users.findById(viewerId).orElseThrow(CommonExceptions.Unauthenticated::new);
        if (u.getUserType() != UserCategoryEnum.ESTABELECIMENTO && u.getUserType() != UserCategoryEnum.VAREJO) {
            throw new CommonExceptions.Forbidden("Só contas de Estabelecimento ou de Varejo & Marca cadastram lugares. "
                    + "Para indicar um lugar, avalie ou publique sobre ele.");
        }
        if (u.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new CommonExceptions.Forbidden("Sua conta precisa estar ativa. Confirme seu e-mail ou fale com o suporte.");
        }
        return u;
    }

    private Establishment requireOwned(long id, long viewerId) {
        Establishment e = establishments.findById(id).orElseThrow(this::notFound);
        if (!Objects.equals(e.getOwnerId(), viewerId)) {
            throw notFound();
        }
        return e;
    }

    private CommonExceptions.NotFound notFound() {
        return new CommonExceptions.NotFound("Estabelecimento não encontrado.");
    }

    static String informalFormat(UserAccount owner) {
        Object v = owner.getTypeDetails() == null ? null : owner.getTypeDetails().get("informalFormat");
        return v == null ? null : v.toString();
    }

    private static void apply(Establishment e, Location l) {
        e.setLocation(l.uf(), l.municipalityIbge(), l.city(), l.neighborhood(), l.street(), l.streetNumber(),
                l.complement(), l.cep(), l.latitude(), l.longitude(), l.geoPrecision(), l.visibility());
    }

    private static void apply(Establishment e, Classification c) {
        e.classify(c.primaryCuisine(), c.secondaryCuisines(), c.primaryFormat(), c.secondaryFormats(), c.approach(),
                c.dietaryOffers());
    }

    private static ClassificationDraft draftOf(Establishment e) {
        Map<String, String> offers = new LinkedHashMap<>();
        e.getDietaryOffers().forEach((k, v) -> offers.put(k.name(), v.name()));
        return new ClassificationDraft(e.getPrimaryCuisine() == null ? null : e.getPrimaryCuisine().name(),
                e.getSecondaryCuisines().stream().map(Enum::name).toList(),
                e.getPrimaryFormat() == null ? null : e.getPrimaryFormat().name(),
                e.getSecondaryFormats().stream().map(Enum::name).toList(), e.getApproach().name(), offers);
    }

    // ------------------------------------------------------------ página

    EstablishmentView view(Establishment e, Optional<UserAccount> viewer) {
        Optional<UserAccount> owner = viewer.filter(v -> Objects.equals(v.getId(), e.getOwnerId()))
                .or(() -> users.findById(e.getOwnerId()));
        boolean isOwner = viewer.isPresent() && Objects.equals(viewer.get().getId(), e.getOwnerId());
        List<Review> counting = reviews.findCounting(e.getId(), AccountStatus.ACTIVE);
        List<String> blockers = isOwner ? publishBlockers(e, owner) : null;
        return new EstablishmentView(e.getId(), e.getName(), e.getDescription(), type(e.getPrimaryType()),
                e.getSecondaryTypes().stream().map(EstablishmentService::type).toList(),
                new Coded(e.getTypeGroup().name(), e.getTypeGroup().getLabel(), null),
                classification(e), price(e, counting), location(e, isOwner, owner), rating(counting),
                owner.map(EstablishmentService::ownerView).orElse(null), e.isAlcoholFocused(), e.getStatus(),
                isOwner ? blockers.isEmpty() : null, blockers, e.getCreatedAt(), e.getUpdatedAt());
    }

    RatingView rating(List<Review> counting) {
        RatingCalculator.Summary s = ratings.summarize(counting.stream()
                .map(r -> new RatingCalculator.Sample(r.getRating(), r.getTrust().weight(), r.getUpdatedAt(),
                        r.getDimensions()))
                .toList());
        Map<String, java.math.BigDecimal> dims = new LinkedHashMap<>();
        s.dimensions().forEach((k, v) -> dims.put(k.name(), v));
        return new RatingView(s.state().name(), s.average(), s.averageLabel(), s.count(), s.histogram(), dims,
                s.accessibleLabel());
    }

    PriceView price(Establishment e, List<Review> counting) {
        if (e.isPriceNotApplicable()) {
            return priceView(PriceLevel.PRICE_NA, null, 0, e);
        }
        List<Integer> reports = counting.stream()
                .filter(r -> r.getSpentPerPersonCents() != null
                        && r.getUpdatedAt().isAfter(clock.instant().minus(PRICE_REPORT_WINDOW)))
                .map(Review::getSpentPerPersonCents).sorted().toList();
        if (reports.size() >= minPriceReports) {
            int n = reports.size();
            int median = n % 2 == 1 ? reports.get(n / 2) : (reports.get(n / 2 - 1) + reports.get(n / 2)) / 2;
            return priceView(priceScale.levelOf(median), PriceLevel.Source.PRICE_SRC_REVIEWS, n, e);
        }
        if (e.getPriceMinCents() != null) {
            int mid = e.getPriceMaxCents() == null ? e.getPriceMinCents()
                    : (e.getPriceMinCents() + e.getPriceMaxCents()) / 2;
            return priceView(priceScale.levelOf(mid), PriceLevel.Source.PRICE_SRC_DECLARED, reports.size(), e);
        }
        return priceView(PriceLevel.PRICE_UNKNOWN, null, reports.size(), e);
    }

    private PriceView priceView(PriceLevel level, PriceLevel.Source source, int reports, Establishment e) {
        String range = priceScale.rangeLabel(level);
        String accessible = range == null ? "Faixa de preço: " + level.getLabel().toLowerCase(java.util.Locale.ROOT)
                : "Faixa de preço: " + level.getLabel().toLowerCase(java.util.Locale.ROOT) + ", " + range + " por pessoa";
        if (source == PriceLevel.Source.PRICE_SRC_REVIEWS) {
            accessible += ", segundo " + reports + " avaliações";
        } else if (source == PriceLevel.Source.PRICE_SRC_DECLARED) {
            accessible += ", informado pelo estabelecimento";
        }
        return new PriceView(level, level.getGlyph(), level.getLabel(), range == null ? null : range + " por pessoa",
                source, reports, e.getPriceMinCents(), e.getPriceMaxCents(), accessible);
    }

    /**
     * A regra da cozinha caseira vale pelo cadastro atual da conta dona, não só pelo do lugar: se a conta passar a
     * ser {@code COZINHA_CASEIRA} depois, o endereço completo já gravado deixa de sair para o público.
     */
    static LocationView location(Establishment e, boolean isOwner, Optional<UserAccount> owner) {
        AddressVisibility v = e.getAddressVisibility();
        if (v == AddressVisibility.ADDR_PUBLIC_FULL && owner.map(EstablishmentService::informalFormat)
                .filter("COZINHA_CASEIRA"::equals).isPresent()) {
            v = AddressVisibility.ADDR_PUBLIC_BAIRRO;
        }
        if (isOwner || v == AddressVisibility.ADDR_PUBLIC_FULL) {
            return new LocationView(e.getCountry(), e.getUf(), e.getMunicipalityIbge(), e.getCity(),
                    e.getNeighborhood(), e.getStreet(), e.getStreetNumber(), e.getAddressComplement(), e.getCep(),
                    e.getLatitude(), e.getLongitude(), e.getGeoPrecision().name(), e.getAddressVisibility().name(), false);
        }
        if (v == AddressVisibility.ADDR_PUBLIC_BAIRRO) {
            boolean point = e.getLatitude() != null;
            return new LocationView(e.getCountry(), e.getUf(), e.getMunicipalityIbge(), e.getCity(),
                    e.getNeighborhood(), null, null, null, null,
                    point ? EstablishmentRules.publicCoordinate(e.getLatitude()) : null,
                    point ? EstablishmentRules.publicCoordinate(e.getLongitude()) : null,
                    "GEO_BAIRRO_CENTROID", v.name(), true);
        }
        return new LocationView(e.getCountry(), e.getUf(), e.getMunicipalityIbge(), e.getCity(), null, null, null,
                null, null, null, null, "GEO_MUNICIPIO_CENTROID", v.name(), true);
    }

    static ClassificationView classification(Establishment e) {
        List<DietaryOfferView> offers = e.getDietaryOffers().entrySet().stream()
                .map(x -> new DietaryOfferView(x.getKey().name(), x.getKey().getLabel(), x.getValue().name())).toList();
        return new ClassificationView(cuisine(e.getPrimaryCuisine()),
                e.getSecondaryCuisines().stream().map(EstablishmentService::cuisine).toList(),
                format(e.getPrimaryFormat()), e.getSecondaryFormats().stream().map(EstablishmentService::format).toList(),
                new Coded(e.getApproach().name(), e.getApproach().getLabel(), null), offers,
                ChipView.of(CuisineChip.of(e.getPrimaryCuisine(), e.getPrimaryFormat(), e.getDietaryOffers())));
    }

    static Coded type(EstablishmentType t) {
        return new Coded(t.name(), t.getLabel(), t.getGroup().name());
    }

    static Coded cuisine(Cuisine c) {
        return c == null ? null : new Coded(c.name(), c.getLabel(), c.getMacro().name());
    }

    static Coded format(FoodFormat f) {
        return f == null ? null : new Coded(f.name(), f.getLabel(), f.getFamily().name());
    }

    static OwnerView ownerView(UserAccount u) {
        return new OwnerView(u.getId(), u.getName(), u.getHandle(), CategoryView.of(u.getUserType()),
                u.getCategoryVerification() == CategoryVerification.VERIFIED);
    }
}
