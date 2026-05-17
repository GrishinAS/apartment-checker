package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserFilterPreferenceRepository extends JpaRepository<UserFilterPreference, Long> {
    List<UserFilterPreference> findAllByUserId(Long userId);
    List<UserFilterPreference> findBySelectedCommunity(String community);
}
