package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.FloorPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FloorPlanRepository extends JpaRepository<FloorPlan, String> {
}
