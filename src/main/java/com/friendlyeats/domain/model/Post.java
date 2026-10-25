package com.friendlyeats.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Post de comida (tabela {@code post}, V3 · RF9): foto de prato, receita, pergunta, prato do dia, promoção, evento ou
 * alerta sanitário. Os vocabulários (culinária, formato, estilos, alergênicos) ficam como códigos estáveis da taxonomia.
 *
 * <p>Os contadores são só leitura para o JPA ({@code insertable = false, updatable = false}): quem os muda é o
 * {@code PostInteractionService}, com UPDATE atômico na mesma transação da interação (RF12). Assim uma entidade
 * carregada antes nunca sobrescreve um contador mais novo.
 */
@Entity
@Table(name = "post")
public class Post implements Persistable<UUID> {

    public static final String REMOVED_TITLE = "Conteúdo removido";

    /** Receita (RF9): ingredientes e passos em texto, rendimento e tempo opcionais. */
    public record Recipe(List<String> ingredients, List<String> steps, Integer servings, Integer prepMinutes) {
    }

    /** Alerta sanitário ou recall (RDC 655/2022): produto, marca, lotes, motivo e a fonte oficial (https). */
    public record FoodAlert(String product, String brand, List<String> lots, String reason, String sourceUrl) {
    }

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    /** Tipo de conta copiado na publicação (cor e filtro sem join). */
    @Enumerated(EnumType.STRING)
    @Column(name = "author_user_type", nullable = false, updatable = false)
    private UserCategoryEnum authorUserType;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false)
    private PostKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "feed_type", nullable = false, updatable = false)
    private FeedType feedType;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false)
    private MediaType mediaType = MediaType.TEXT;

    @Column(name = "title", nullable = false, length = 140)
    private String title;

    @Column(name = "body", length = 5000)
    private String body;

    @Column(name = "media_object_key", length = 512)
    private String mediaObjectKey;

    @Column(name = "alt_text", length = 500)
    private String altText;

    @Column(name = "establishment_id")
    private Long establishmentId;

    @Column(name = "cuisine", length = 32)
    private String cuisine;

    @Column(name = "food_format", length = 32)
    private String foodFormat;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dietary_styles", nullable = false)
    private List<String> dietaryStyles = new ArrayList<>();

    /** {@code {"LEITE": "CONTEM", "AMENDOIM": "PODE_CONTER"}}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "allergens", nullable = false)
    private Map<String, String> allergens = new LinkedHashMap<>();

    /** Advertências (LACTOSE, GLUTEN), num campo próprio: não são alergênicos. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "food_warnings", nullable = false)
    private List<String> foodWarnings = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", nullable = false)
    private AgeRating ageRating = AgeRating.LIVRE;

    /** Chip "Publicidade": relação comercial declarada (publi, permuta, convite · CDC art. 36). */
    @Column(name = "advertising", nullable = false)
    private boolean advertising;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    /** Sai do feed depois disto: 23h59 do dia (prato do dia) ou o fim da vigência (promoção, evento). */
    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "price_cents")
    private Integer priceCents;

    @Column(name = "price_from_cents")
    private Integer priceFromCents;

    @Column(name = "price_to_cents")
    private Integer priceToCents;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "free_entry")
    private Boolean freeEntry;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recipe")
    private Recipe recipe;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "food_alert")
    private FoodAlert foodAlert;

    @Column(name = "city", length = 120)
    private String city;

    /** Moldura do post (RF16): forma canônica do {@code BackgroundValidator}; {@code null} = moldura padrão. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "background")
    private Map<String, Object> background;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PostStatus status = PostStatus.DRAFT;

    @Column(name = "like_count", insertable = false, updatable = false)
    private long likeCount;

    @Column(name = "comment_count", insertable = false, updatable = false)
    private long commentCount;

    @Column(name = "share_count", insertable = false, updatable = false)
    private long shareCount;

    /** Data de publicação: relativa no card, absoluta (com fuso) na página do post. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Transient
    private boolean isNew = true;

    protected Post() {
        // JPA
    }

    public Post(UserAccount author, PostKind kind, String title) {
        this.id = UuidV7.next();
        this.authorId = Objects.requireNonNull(author.getId(), "autor precisa estar persistido");
        this.authorUserType = author.getUserType();
        this.kind = kind;
        this.feedType = kind.feed();
        this.title = title;
    }

    @PrePersist
    void onCreate() {
        // micro-segundos: a mesma precisão do TIMESTAMPTZ, para o cursor (created_at, id) bater exato
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        isNew = false;
    }

    // ---------------------------------------------------------------- ciclo de vida

    public void publish() {
        transitionTo(PostStatus.PUBLISHED);
    }

    public void archive() {
        transitionTo(PostStatus.ARCHIVED);
    }

    /** Autor, moderação ou eliminação da conta: apaga o conteúdo e mantém só o id e os metadados técnicos. */
    public void remove() {
        transitionTo(PostStatus.REMOVED);
        this.title = REMOVED_TITLE;
        this.body = null;
        this.mediaObjectKey = null;
        this.altText = null;
        this.recipe = null;
        this.foodAlert = null;
        this.city = null;
        this.background = null;
        this.priceCents = null;
        this.priceFromCents = null;
        this.priceToCents = null;
        this.discountPercent = null;
        this.removedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    /** Rascunho só para o autor; removido para ninguém (RF14). As regras de conta, 18+ e perfil privado ficam no serviço. */
    public boolean isVisibleTo(Long viewerId) {
        return switch (status) {
            case PUBLISHED, ARCHIVED -> true;
            case DRAFT -> authorId.equals(viewerId);
            case REMOVED -> false;
        };
    }

    /** RF12: curtir, comentar, compartilhar e salvar só em post publicado. */
    public boolean acceptsInteractions() {
        return status == PostStatus.PUBLISHED;
    }

    /** Alerta sanitário recebe só compartilhar e salvar: sem curtida nem comentário, para não virar discussão. */
    public boolean acceptsLikesAndComments() {
        return acceptsInteractions() && kind != PostKind.ALERTA_SANITARIO;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public boolean isAuthoredBy(Long userId) {
        return authorId.equals(userId);
    }

    private void transitionTo(PostStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException("Transição inválida: " + status + " -> " + target);
        }
        this.status = target;
    }

    // ---------------------------------------------------------------- getters / setters

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    public Long getAuthorId() { return authorId; }
    public UserCategoryEnum getAuthorUserType() { return authorUserType; }
    public PostKind getKind() { return kind; }
    public FeedType getFeedType() { return feedType; }
    public MediaType getMediaType() { return mediaType; }
    public void setMediaType(MediaType mediaType) { this.mediaType = mediaType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getMediaObjectKey() { return mediaObjectKey; }
    public void setMediaObjectKey(String mediaObjectKey) { this.mediaObjectKey = mediaObjectKey; }
    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }
    public Long getEstablishmentId() { return establishmentId; }
    public void setEstablishmentId(Long establishmentId) { this.establishmentId = establishmentId; }
    public String getCuisine() { return cuisine; }
    public void setCuisine(String cuisine) { this.cuisine = cuisine; }
    public String getFoodFormat() { return foodFormat; }
    public void setFoodFormat(String foodFormat) { this.foodFormat = foodFormat; }
    public List<String> getDietaryStyles() { return dietaryStyles; }
    public void setDietaryStyles(List<String> v) { this.dietaryStyles = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public Map<String, String> getAllergens() { return allergens; }
    public void setAllergens(Map<String, String> v) { this.allergens = v == null ? new LinkedHashMap<>() : new LinkedHashMap<>(v); }
    public List<String> getFoodWarnings() { return foodWarnings; }
    public void setFoodWarnings(List<String> v) { this.foodWarnings = v == null ? new ArrayList<>() : new ArrayList<>(v); }
    public AgeRating getAgeRating() { return ageRating; }
    public void setAgeRating(AgeRating ageRating) { this.ageRating = ageRating; }
    public boolean isAdvertising() { return advertising; }
    public void setAdvertising(boolean advertising) { this.advertising = advertising; }
    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Integer getPriceCents() { return priceCents; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }
    public Integer getPriceFromCents() { return priceFromCents; }
    public void setPriceFromCents(Integer priceFromCents) { this.priceFromCents = priceFromCents; }
    public Integer getPriceToCents() { return priceToCents; }
    public void setPriceToCents(Integer priceToCents) { this.priceToCents = priceToCents; }
    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }
    public Boolean getFreeEntry() { return freeEntry; }
    public void setFreeEntry(Boolean freeEntry) { this.freeEntry = freeEntry; }
    public Recipe getRecipe() { return recipe; }
    public void setRecipe(Recipe recipe) { this.recipe = recipe; }
    public FoodAlert getFoodAlert() { return foodAlert; }
    public void setFoodAlert(FoodAlert foodAlert) { this.foodAlert = foodAlert; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public Map<String, Object> getBackground() { return background; }
    public void setBackground(Map<String, Object> background) { this.background = background; }
    public PostStatus getStatus() { return status; }
    public long getLikeCount() { return likeCount; }
    public long getCommentCount() { return commentCount; }
    public long getShareCount() { return shareCount; }
    public Instant getCreatedAt() { return createdAt; }
    /** Só para dados de exemplo e testes; na publicação vale a hora do INSERT. */
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt == null ? null : createdAt.truncatedTo(ChronoUnit.MICROS); }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getRemovedAt() { return removedAt; }
}
