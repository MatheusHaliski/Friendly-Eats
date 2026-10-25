package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.AccountStatus;
import com.friendlyeats.domain.model.UserCategoryEnum;
import com.friendlyeats.domain.model.UserAccount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByEmail(String email);

    /** Job diário (RF5): contas cujo prazo de 30 dias para reativar terminou. */
    java.util.List<UserAccount> findByAccountStatusAndDeletionScheduledForBefore(AccountStatus status, java.time.Instant now);

    boolean existsByEmail(String email);

    boolean existsByHandle(String handle);

    /** Profile Feed: filtros Categoria + País + Cidade. */
    Page<UserAccount> findByUserTypeAndCountryOfResidenceAndCityOfResidenceAndAccountStatus(
            UserCategoryEnum userType, String country, String city, AccountStatus status, Pageable pageable);

    /** Profile Feed: filtro por Nome. */
    Page<UserAccount> findByNameContainingIgnoreCaseAndAccountStatus(
            String name, AccountStatus status, Pageable pageable);
}
