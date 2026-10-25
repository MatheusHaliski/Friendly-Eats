package com.friendlyeats.infrastructure.persistence.postgres;

import com.friendlyeats.domain.model.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
}
