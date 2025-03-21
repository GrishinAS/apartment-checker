package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.FloorPlanGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FloorPlanGroupRepository extends JpaRepository<FloorPlanGroup, Long> {
    FloorPlanGroup findByGroupType(String groupType);
}