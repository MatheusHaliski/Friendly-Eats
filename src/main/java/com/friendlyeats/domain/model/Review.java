package com.friendlyeats.domain.model;

import com.friendlyeats.domain.taxonomy.ReviewDimension;
import com.friendlyeats.domain.taxonomy.VisitMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Avaliação com estrelas (tabela {@code review}, V2 · RF8). Uma por pessoa e estabelecimento; a edição substitui a
 * nota e guarda a versão anterior em {@link ReviewRevision}.
 */
@Entity
@Table(name = "review")
public class Review {

    /** Publicada direto; {@code UNDER_REVIEW} e {@code REMOVED} vêm da moderação (RF13). */
    public enum Status { PUBLISHED, UNDER_REVIEW, REMOVED }

    /** O que quem avalia declara sobre a relação com o lugar (RF13). */
    public enum Disclosure { NONE, COURTESY, INVITATION, RELATIONSHIP }

    /** Quanto a avaliação pesa no agregado: verificada por pedido, nota fiscal ou check-in pesa mais. */
    public enum Trust {
        REVIEW_VERIFIED_ORDER(1.0), REVIEW_VERIFIED_RECEIPT(1.0), REVIEW_VERIFIED_CHECKIN(1.0),
        REVIEW_UNVERIFIED(0.6), REVIEW_INCENTIVIZED(0.6), REVIEW_CONFLICT(0.0);

        private final double weight;

        Trust(double weight) {
            this.weight = weight;
        }

        public double weight() {
            return weight;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "establishment_id", nullable = false, updatable = false)
    private Long establishmentId;

    @Column(name = "author_id", nullable = false, updatable = false)
    private Long authorId;

    @Column(name = "rating", nullable = false)
    private short rating;

    @Enumerated(EnumType.STRING)
    @Column(name = "visit_mode", nullable = false)
    private VisitMode visitMode;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dimensions", nullable = false)
    private Map<String, Integer> dimensions = new LinkedHashMap<>();

    @Column(name = "spent_per_person_cents")
    private Integer spentPerPersonCents;

    @Column(name = "body", length = 2000)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "trust", nullable = false)
    private Trust trust = Trust.REVIEW_UNVERIFIED;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PUBLISHED;

    @Column(name = "edit_count", nullable = false)
    private int editCount;

    /** HMAC do IP de quem escreveu (RF13): só para o sinal de mesma origem, nunca o IP. */
    @Column(name = "origin_hash", length = 64)
    private String originHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Data da experiência avaliada: base do peso de recência. */
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Review() {
        // JPA
    }

    public Review(long establishmentId, long authorId) {
        this.establishmentId = establishmentId;
        this.authorId = authorId;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    /** Substitui o conteúdo; quem chama guarda antes a versão anterior. */
    public void write(int rating, VisitMode visitMode, Map<ReviewDimension, Integer> dimensions,
                      Integer spentPerPersonCents, String body, Instant when) {
        this.rating = (short) rating;
        this.visitMode = visitMode;
        Map<String, Integer> dims = new LinkedHashMap<>();
        dimensions.forEach((d, v) -> dims.put(d.name(), v));
        this.dimensions = dims;
        this.spentPerPersonCents = spentPerPersonCents;
        this.body = body;
        if (id != null) {
            editCount++;
        } else {
            createdAt = when;
        }
        this.updatedAt = when;
    }

    /**
     * Vínculo declarado por quem avalia (RF13 · RC-SEG-04): cortesia ou convite viram {@code REVIEW_INCENTIVIZED}
     * (peso menor), relação com o lugar vira {@code REVIEW_CONFLICT} (fora da nota). Avaliação verificada não muda.
     */
    public void declare(Disclosure disclosure) {
        if (trust == Trust.REVIEW_VERIFIED_ORDER || trust == Trust.REVIEW_VERIFIED_RECEIPT
                || trust == Trust.REVIEW_VERIFIED_CHECKIN || disclosure == null) {
            return;
        }
        trust = switch (disclosure) {
            case NONE -> Trust.REVIEW_UNVERIFIED;
            case COURTESY, INVITATION -> Trust.REVIEW_INCENTIVIZED;
            case RELATIONSHIP -> Trust.REVIEW_CONFLICT;
        };
    }

    /** Sinal automático ou retirada preventiva: sai da lista e do agregado até a decisão (RF13). */
    public void putUnderReview() {
        if (status == Status.PUBLISHED) {
            status = Status.UNDER_REVIEW;
        }
    }

    /** Decisão de moderação: removida com motivo (o motivo fica na decisão do caso). */
    public void removeByModeration() {
        status = Status.REMOVED;
    }

    /** Decisão "manter" ou recurso procedente: volta para a lista e para o agregado. */
    public void restoreByModeration() {
        status = Status.PUBLISHED;
    }

    public void setOriginHash(String originHash) {
        this.originHash = originHash;
    }

    public String getOriginHash() {
        return originHash;
    }

    public Map<ReviewDimension, Integer> getDimensions() {
        Map<ReviewDimension, Integer> out = new EnumMap<>(ReviewDimension.class);
        dimensions.forEach((k, v) -> out.put(ReviewDimension.valueOf(k), v));
        return out;
    }

    public Long getId() { return id; }
    public Long getEstablishmentId() { return establishmentId; }
    public Long getAuthorId() { return authorId; }
    public int getRating() { return rating; }
    public VisitMode getVisitMode() { return visitMode; }
    public Integer getSpentPerPersonCents() { return spentPerPersonCents; }
    public String getBody() { return body; }
    public Trust getTrust() { return trust; }
    public Status getStatus() { return status; }
    public int getEditCount() { return editCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
