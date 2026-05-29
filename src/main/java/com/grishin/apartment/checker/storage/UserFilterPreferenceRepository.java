package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserFilterPreferenceRepository extends JpaRepository<UserFilterPreference, Long> {
    @Override
    @EntityGraph(attributePaths = "amenities")
    Optional<UserFilterPreference> findById(Long id);

    @EntityGraph(attributePaths = "amenities")
    List<UserFilterPreference> findAllByUserId(Long userId);

    @EntityGraph(attributePaths = "amenities")
    Optional<UserFilterPreference> findByUserIdAndSelectedCommunity(Long userId, String selectedCommunity);

    @EntityGraph(attributePaths = "amenities")
    List<UserFilterPreference> findBySelectedCommunity(String community);
}
