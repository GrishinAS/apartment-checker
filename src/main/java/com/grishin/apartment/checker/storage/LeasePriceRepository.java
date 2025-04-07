package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.LeasePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LeasePriceRepository extends JpaRepository<LeasePrice, Long> {
    Optional<LeasePrice> findByUnitObjectId(String objectId);
}