package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.ReviewRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReviewRevisionRepository extends JpaRepository<ReviewRevision, Long> {

    List<ReviewRevision> findByReviewIdOrderByReplacedAtAsc(Long reviewId);
}
