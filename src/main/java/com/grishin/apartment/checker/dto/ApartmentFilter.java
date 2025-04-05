package com.grishin.apartment.checker.dto;

import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class ApartmentFilter {
    private Boolean isStudio;
    private Integer minBedrooms;
    private Integer maxBedrooms;
    private Integer minBathrooms;
    private Integer maxBathrooms;
    private Integer minPrice;
    private Integer maxPrice;
    private Integer minFloor;
    private Integer maxFloor;
    private List<String> amenities;
    private Date minDate;
    private Date maxDate;

    public static ApartmentFilter createFrom(UserFilterPreference filter) {
        ApartmentFilter apartmentFilter = new ApartmentFilter();
        apartmentFilter.setIsStudio(filter.getIsStudio());
        apartmentFilter.setMinBedrooms(filter.getMinBedrooms());
        apartmentFilter.setMaxBedrooms(filter.getMaxBedrooms());
        apartmentFilter.setMinBathrooms(filter.getMinBathrooms());
        apartmentFilter.setMaxBathrooms(filter.getMaxBathrooms());
        apartmentFilter.setMinPrice(filter.getMinPrice());
        apartmentFilter.setMaxPrice(filter.getMaxPrice());
        apartmentFilter.setMinFloor(filter.getMinFloor());
        apartmentFilter.setMaxFloor(filter.getMaxFloor());
        apartmentFilter.setMinDate(filter.getAvailableFrom());
        apartmentFilter.setMaxDate(filter.getAvailableUntil());
        return apartmentFilter;
    }
}
