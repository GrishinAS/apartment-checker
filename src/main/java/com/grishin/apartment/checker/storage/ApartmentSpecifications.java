package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.storage.entity.FloorPlan;
import com.grishin.apartment.checker.storage.entity.LeasePrice;
import com.grishin.apartment.checker.storage.entity.Unit;
import com.grishin.apartment.checker.storage.entity.UnitAmenity;
import jakarta.annotation.Nullable;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class ApartmentSpecifications {

    public static Specification<Unit> filterBy(ApartmentFilter filter) {
        return filterBy(filter, null);
    }

    public static Specification<Unit> filterBy(ApartmentFilter filter, @Nullable List<String> optionalObjectIds) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            query.distinct(true);

            Join<Unit, FloorPlan> floorPlanJoin = root.join("floorPlan", JoinType.LEFT);
            Join<Unit, LeasePrice> leasePriceJoin = root.join("unitEarliestAvailable", JoinType.LEFT);

            if (optionalObjectIds != null) {
                predicates.add(root.get("objectId").in(optionalObjectIds));
            }

            if (filter.getIsStudio() != null) {
                predicates.add(criteriaBuilder.equal(root.get("unitIsStudio"), filter.getIsStudio()));
            }

            if (filter.getMinBedrooms() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(floorPlanJoin.get("floorPlanBed"), filter.getMinBedrooms()));
            }

            if (filter.getMaxBedrooms() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(floorPlanJoin.get("floorPlanBed"), filter.getMaxBedrooms()));
            }

            if (filter.getMinBathrooms() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(floorPlanJoin.get("floorPlanBath"), filter.getMinBathrooms()));
            }

            if (filter.getMaxBathrooms() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(floorPlanJoin.get("floorPlanBath"), filter.getMaxBathrooms()));
            }

            if (filter.getMinPrice() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(leasePriceJoin.get("price"), filter.getMinPrice()));
            }

            if (filter.getMaxPrice() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(leasePriceJoin.get("price"), filter.getMaxPrice()));
            }

            if (filter.getMinFloor() != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(root.get("unitFloor"), filter.getMinFloor()));
            }

            if (filter.getMaxFloor() != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(root.get("unitFloor"), filter.getMaxFloor()));
            }

            if (filter.getAmenities() != null && !filter.getAmenities().isEmpty()) {
                Join<Unit, UnitAmenity> amenityJoin = root.join("amenities", JoinType.INNER);

                Predicate amenitiesPredicate = amenityJoin.get("amenityName").in(filter.getAmenities());

                Subquery<Long> amenityCountSubquery = query.subquery(Long.class);
                Root<Unit> subRoot = amenityCountSubquery.from(Unit.class);
                Join<Unit, UnitAmenity> subAmenityJoin = subRoot.join("amenities", JoinType.INNER);

                amenityCountSubquery.select(criteriaBuilder.count(subAmenityJoin.get("id")));
                amenityCountSubquery.where(
                        criteriaBuilder.and(
                                criteriaBuilder.equal(subRoot.get("objectId"), root.get("objectId")),
                                subAmenityJoin.get("amenityName").in(filter.getAmenities())
                        )
                );

                predicates.add(criteriaBuilder.equal(amenityCountSubquery, (long) filter.getAmenities().size()));
            }

            if (filter.getMinDate() != null || filter.getMaxDate() != null) {
                if (filter.getMinDate() != null) {
                    predicates.add(criteriaBuilder.greaterThanOrEqualTo(leasePriceJoin.get("availableDate"), filter.getMinDate()));
                }

                if (filter.getMaxDate() != null) {
                    predicates.add(criteriaBuilder.lessThanOrEqualTo(leasePriceJoin.get("availableDate"), filter.getMaxDate()));
                }
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
