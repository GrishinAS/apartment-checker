package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, String> {
    List<Unit> findByCommunityId(String communityId);
    List<Unit> findByFloorPlanFloorPlanUniqueId(String floorPlanUniqueId);
}
