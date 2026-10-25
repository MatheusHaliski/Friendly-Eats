package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.UserMfa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserMfaRepository extends JpaRepository<UserMfa, Long> {
}
