package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.UnitAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UnitAmenityRepository extends JpaRepository<UnitAmenity, Long> {
    Optional<UnitAmenity> findByAmenityName(String amenityName);
}
