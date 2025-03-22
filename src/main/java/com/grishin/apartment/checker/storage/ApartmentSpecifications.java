package com.grishin.apartment.checker.storage;

import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.storage.entity.Unit;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class ApartmentSpecifications {

    public static Specification<Unit> withFilters(ApartmentFilter filters) {
        return Specification
                .where(isStudioEquals(filters.getIsStudio()))
                .and(bedroomsBetween(filters.getMinBedrooms(), filters.getMaxBedrooms()))
                .and(bathroomsBetween(filters.getMinBathrooms(), filters.getMaxBathrooms()))
                .and(priceBetween(filters.getMinPrice(), filters.getMaxPrice()))
                .and(floorBetween(filters.getMinFloor(), filters.getMaxFloor()))
                .and(hasStainlessAppliancesEquals(filters.getHasStainlessAppliances()))
                .and(availableFrom(filters.getEarliestAvailableFrom()));
    }

    private static Specification<Unit> isStudioEquals(Boolean isStudio) {
        return (isStudio == null) ? null : (root, query, cb) ->
                cb.equal(root.get("isStudio"), isStudio);
    }

    private static Specification<Unit> bedroomsBetween(Integer min, Integer max) {
        if (min == null && max == null) return null;

        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("floorplanBed"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("floorplanBed"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("floorplanBed"), max);
            }
        };
    }

    private static Specification<Unit> bathroomsBetween(Double min, Double max) {
        if (min == null && max == null) return null;

        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("floorplanBath"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("floorplanBath"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("floorplanBath"), max);
            }
        };
    }

    private static Specification<Unit> priceBetween(Double min, Double max) {
        if (min == null && max == null) return null;

        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("price"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("price"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("price"), max);
            }
        };
    }

    private static Specification<Unit> floorBetween(Integer min, Integer max) {
        if (min == null && max == null) return null;

        return (root, query, cb) -> {
            if (min != null && max != null) {
                return cb.between(root.get("unitFloor"), min, max);
            } else if (min != null) {
                return cb.greaterThanOrEqualTo(root.get("unitFloor"), min);
            } else {
                return cb.lessThanOrEqualTo(root.get("unitFloor"), max);
            }
        };
    }

    private static Specification<Unit> hasStainlessAppliancesEquals(Boolean hasStainlessAppliances) {
        return (hasStainlessAppliances == null) ? null : (root, query, cb) ->
                cb.equal(root.get("hasStainlessAppliances"), hasStainlessAppliances);
    }

    private static Specification<Unit> availableFrom(String earliestAvailableFrom) {
        if (earliestAvailableFrom == null || earliestAvailableFrom.isEmpty()) return null;

        return (root, query, cb) -> {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
                LocalDate availableDate = LocalDate.parse(earliestAvailableFrom, formatter);
                return cb.greaterThanOrEqualTo(root.get("unitEarliestAvailable"), availableDate);
            } catch (Exception e) {
                // If date parsing fails, ignore this filter
                return cb.conjunction();
            }
        };
    }

    private static Specification<Unit> buildingNumberEquals(String buildingNumber) {
        if (buildingNumber == null || buildingNumber.isEmpty()) return null;

        return (root, query, cb) ->
                cb.equal(root.get("buildingNumber"), buildingNumber);
    }

    private static Specification<Unit> floorplanNameContains(String floorplanName) {
        if (floorplanName == null || floorplanName.isEmpty()) return null;

        return (root, query, cb) ->
                cb.like(cb.lower(root.get("floorplanName")), "%" + floorplanName.toLowerCase() + "%");
    }
}
