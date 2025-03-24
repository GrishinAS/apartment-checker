package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFilterPreferenceRepository extends JpaRepository<UserFilterPreference, Long> {
    Optional<UserFilterPreference> findByUserId(Long userId);
    List<UserFilterPreference> findBySelectedCommunity(String community);
}
