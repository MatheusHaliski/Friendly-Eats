package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    List<Establishment> findByOwnerIdOrderByCreatedAtAsc(Long ownerId);

    long countByOwnerId(Long ownerId);

    /** Eliminação da conta dona (RF5); as avaliações do lugar saem junto (ON DELETE CASCADE). */
    @Modifying
    @Query("delete from Establishment e where e.ownerId = :ownerId")
    int deleteAllOwnedBy(Long ownerId);
}
