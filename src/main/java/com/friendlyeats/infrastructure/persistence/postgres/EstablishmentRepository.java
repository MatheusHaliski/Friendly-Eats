package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.Establishment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface EstablishmentRepository extends JpaRepository<Establishment, Long> {

    List<Establishment> findByOwnerIdOrderByCreatedAtAsc(Long ownerId);

    long countByOwnerId(Long ownerId);

    /**
     * Trava a linha da conta dona até o fim da transação: dois cadastros simultâneos do mesmo negócio com CPF
     * ficam em fila, e o segundo já conta o primeiro (um único ponto de venda).
     */
    @Query(value = "SELECT id FROM user_account WHERE id = :ownerId FOR UPDATE", nativeQuery = true)
    Long lockOwner(Long ownerId);

    /** Eliminação da conta dona (RF5); as avaliações do lugar saem junto (ON DELETE CASCADE). */
    @Modifying
    @Query("delete from Establishment e where e.ownerId = :ownerId")
    int deleteAllOwnedBy(Long ownerId);
}
