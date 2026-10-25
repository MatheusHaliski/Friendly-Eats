package com.friendlyeats.domain.model;

import com.friendlyeats.domain.taxonomy.VisitMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Versão anterior de uma {@link Review}: a edição substitui a nota, mas o histórico fica (RF8). */
@Entity
@Table(name = "review_revision")
public class ReviewRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "review_id", nullable = false, updatable = false)
    private Long reviewId;

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

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "replaced_at", nullable = false)
    private Instant replacedAt;

    protected ReviewRevision() {
        // JPA
    }

    public static ReviewRevision of(Review r, Instant replacedAt) {
        ReviewRevision v = new ReviewRevision();
        v.reviewId = r.getId();
        v.rating = (short) r.getRating();
        v.visitMode = r.getVisitMode();
        r.getDimensions().forEach((d, value) -> v.dimensions.put(d.name(), value));
        v.spentPerPersonCents = r.getSpentPerPersonCents();
        v.body = r.getBody();
        v.validFrom = r.getUpdatedAt();
        v.replacedAt = replacedAt;
        return v;
    }

    public Long getReviewId() { return reviewId; }
    public int getRating() { return rating; }
    public Instant getValidFrom() { return validFrom; }
    public Instant getReplacedAt() { return replacedAt; }
}
