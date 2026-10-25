package com.friendlyeats.domain.model;

import com.friendlyeats.domain.taxonomy.AddressVisibility;
import com.friendlyeats.domain.taxonomy.Approach;
import com.friendlyeats.domain.taxonomy.Cuisine;
import com.friendlyeats.domain.taxonomy.DietaryStyle;
import com.friendlyeats.domain.taxonomy.EstablishmentGroup;
import com.friendlyeats.domain.taxonomy.EstablishmentType;
import com.friendlyeats.domain.taxonomy.FoodFormat;
import com.friendlyeats.domain.taxonomy.GeoPrecision;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estabelecimento (tabela {@code establishment}, V2): tipo, local e preço declarado (RF6) e classificação (RF7).
 * Os vocabulários ficam como códigos estáveis em JSON; os getters devolvem os enums da taxonomia.
 */
@Entity
@Table(name = "establishment")
public class Establishment {

    /** {@code CLOSED} = fechou de vez (fica para o histórico); {@code REMOVED} = tirado pela moderação. */
    public enum Status { ACTIVE, CLOSED, REMOVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private Long ownerId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_type", nullable = false)
    private EstablishmentType primaryType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_types", nullable = false)
    private List<String> secondaryTypes = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "type_group", nullable = false)
    private EstablishmentGroup typeGroup;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_cuisine")
    private Cuisine primaryCuisine;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_cuisines", nullable = false)
    private List<String> secondaryCuisines = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_format")
    private FoodFormat primaryFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "secondary_formats", nullable = false)
    private List<String> secondaryFormats = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "approach", nullable = false)
    private Approach approach = Approach.TRADICIONAL;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dietary_offers", nullable = false)
    private Map<String, String> dietaryOffers = new LinkedHashMap<>();

    @Column(name = "price_min_cents")
    private Integer priceMinCents;

    @Column(name = "price_max_cents")
    private Integer priceMaxCents;

    @Column(name = "price_not_applicable", nullable = false)
    private boolean priceNotApplicable;

    @Column(name = "country", nullable = false, length = 2)
    private String country = "BR";

    @Column(name = "uf", nullable = false, length = 2)
    private String uf;

    @Column(name = "municipality_ibge", nullable = false, length = 7)
    private String municipalityIbge;

    @Column(name = "city", nullable = false, length = 120)
    private String city;

    @Column(name = "neighborhood", length = 120)
    private String neighborhood;

    @Column(name = "street", length = 160)
    private String street;

    @Column(name = "street_number", length = 20)
    private String streetNumber;

    @Column(name = "address_complement", length = 80)
    private String addressComplement;

    @Column(name = "cep", length = 8)
    private String cep;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "geo_precision", nullable = false)
    private GeoPrecision geoPrecision = GeoPrecision.GEO_MUNICIPIO_CENTROID;

    @Enumerated(EnumType.STRING)
    @Column(name = "address_visibility", nullable = false)
    private AddressVisibility addressVisibility = AddressVisibility.ADDR_PUBLIC_FULL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Establishment() {
        // JPA
    }

    public Establishment(long ownerId, String name) {
        this.ownerId = ownerId;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ------------------------------------------------------------ tipo (RF6)

    /** O grupo sempre acompanha o tipo principal (R3 da taxonomia). */
    public void setTypes(EstablishmentType primary, List<EstablishmentType> secondary) {
        this.primaryType = primary;
        this.typeGroup = primary.getGroup();
        this.secondaryTypes = new ArrayList<>(secondary.stream().map(Enum::name).toList());
    }

    public List<EstablishmentType> getSecondaryTypes() {
        return secondaryTypes.stream().map(EstablishmentType::valueOf).toList();
    }

    /** Página para maiores de 18: o tipo principal tem foco em bebida alcoólica. */
    public boolean isAlcoholFocused() {
        return primaryType.isAlcoholFocused();
    }

    // ------------------------------------------------------------ classificação (RF7)

    public void classify(Cuisine primaryCuisine, List<Cuisine> secondaryCuisines, FoodFormat primaryFormat,
                         List<FoodFormat> secondaryFormats, Approach approach,
                         Map<DietaryStyle, DietaryStyle.Level> dietaryOffers) {
        this.primaryCuisine = primaryCuisine;
        this.secondaryCuisines = new ArrayList<>(secondaryCuisines.stream().map(Enum::name).toList());
        this.primaryFormat = primaryFormat;
        this.secondaryFormats = new ArrayList<>(secondaryFormats.stream().map(Enum::name).toList());
        this.approach = approach;
        Map<String, String> offers = new LinkedHashMap<>();
        dietaryOffers.forEach((style, level) -> offers.put(style.name(), level.name()));
        this.dietaryOffers = offers;
    }

    public List<Cuisine> getSecondaryCuisines() {
        return secondaryCuisines.stream().map(Cuisine::valueOf).toList();
    }

    public List<FoodFormat> getSecondaryFormats() {
        return secondaryFormats.stream().map(FoodFormat::valueOf).toList();
    }

    public Map<DietaryStyle, DietaryStyle.Level> getDietaryOffers() {
        Map<DietaryStyle, DietaryStyle.Level> out = new EnumMap<>(DietaryStyle.class);
        dietaryOffers.forEach((k, v) -> out.put(DietaryStyle.valueOf(k), DietaryStyle.Level.valueOf(v)));
        return out;
    }

    public boolean isClassified() {
        return primaryCuisine != null || primaryFormat != null;
    }

    // ------------------------------------------------------------ preço e local (RF6)

    public void setDeclaredPrice(Integer minCents, Integer maxCents, boolean notApplicable) {
        this.priceMinCents = notApplicable ? null : minCents;
        this.priceMaxCents = notApplicable ? null : maxCents;
        this.priceNotApplicable = notApplicable;
    }

    public void setLocation(String uf, String municipalityIbge, String city, String neighborhood, String street,
                            String streetNumber, String addressComplement, String cep, Double latitude,
                            Double longitude, GeoPrecision geoPrecision, AddressVisibility visibility) {
        this.uf = uf;
        this.municipalityIbge = municipalityIbge;
        this.city = city;
        this.neighborhood = neighborhood;
        this.street = street;
        this.streetNumber = streetNumber;
        this.addressComplement = addressComplement;
        this.cep = cep;
        this.latitude = latitude;
        this.longitude = longitude;
        this.geoPrecision = geoPrecision;
        this.addressVisibility = visibility;
    }

    // ------------------------------------------------------------ getters e setters simples

    public Long getId() { return id; }
    public Long getOwnerId() { return ownerId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public EstablishmentType getPrimaryType() { return primaryType; }
    public EstablishmentGroup getTypeGroup() { return typeGroup; }
    public Cuisine getPrimaryCuisine() { return primaryCuisine; }
    public FoodFormat getPrimaryFormat() { return primaryFormat; }
    public Approach getApproach() { return approach; }
    public Integer getPriceMinCents() { return priceMinCents; }
    public Integer getPriceMaxCents() { return priceMaxCents; }
    public boolean isPriceNotApplicable() { return priceNotApplicable; }
    public String getCountry() { return country; }
    public String getUf() { return uf; }
    public String getMunicipalityIbge() { return municipalityIbge; }
    public String getCity() { return city; }
    public String getNeighborhood() { return neighborhood; }
    public String getStreet() { return street; }
    public String getStreetNumber() { return streetNumber; }
    public String getAddressComplement() { return addressComplement; }
    public String getCep() { return cep; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public GeoPrecision getGeoPrecision() { return geoPrecision; }
    public AddressVisibility getAddressVisibility() { return addressVisibility; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
