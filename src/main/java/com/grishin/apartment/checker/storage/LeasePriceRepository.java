package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.storage.entity.LeasePrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface LeasePriceRepository extends JpaRepository<LeasePrice, Long> {
    List<LeasePrice> findByUnitUnitId(String unitId);
    void deleteByUnitUnitId(String unitId);
}