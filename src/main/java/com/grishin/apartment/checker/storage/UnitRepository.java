package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.Unit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UnitRepository extends JpaRepository<Unit, String>, JpaSpecificationExecutor<Unit> {
    List<Unit> findByCommunityId(String communityId);
}
