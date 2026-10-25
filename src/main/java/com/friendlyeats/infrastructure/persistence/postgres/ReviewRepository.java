package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    Optional<Review> findByEstablishmentIdAndAuthorId(Long establishmentId, Long authorId);

    /**
     * Avaliações que contam (RF8): publicadas e de autor com conta ativa. Conta em carência de exclusão ou suspensa
     * some do agregado e da lista sem perder a avaliação, que volta se a conta for reativada.
     */
    @Query("""
            select r from Review r, UserAccount u
            where u.id = r.authorId and r.establishmentId = :establishmentId
              and r.status = com.friendlyeats.domain.model.Review.Status.PUBLISHED and u.accountStatus = :active
            """)
    List<Review> findCounting(Long establishmentId, AccountStatus active);

    @Query(value = """
            select r, u from Review r, UserAccount u
            where u.id = r.authorId and r.establishmentId = :establishmentId
              and r.status = com.friendlyeats.domain.model.Review.Status.PUBLISHED and u.accountStatus = :active
            order by r.updatedAt desc, r.id desc
            """, countQuery = """
            select count(r) from Review r, UserAccount u
            where u.id = r.authorId and r.establishmentId = :establishmentId
              and r.status = com.friendlyeats.domain.model.Review.Status.PUBLISHED and u.accountStatus = :active
            """)
    Page<Object[]> pageCounting(Long establishmentId, AccountStatus active, Pageable pageable);

    /** Eliminação do autor (RF5); as versões anteriores saem junto (ON DELETE CASCADE). */
    @Modifying
    @Query("delete from Review r where r.authorId = :authorId")
    int deleteAllWrittenBy(Long authorId);
}
