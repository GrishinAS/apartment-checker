package com.grishin.apartment.checker.dto;

import lombok.Data;

@Data
public class ApartmentFilter {
    private Boolean isStudio;
    private Integer minBedrooms;
    private Integer maxBedrooms;
    private Double minBathrooms;
    private Double maxBathrooms;
    private Double minPrice;
    private Double maxPrice;
    private Integer minFloor;
    private Integer maxFloor;
    private Boolean hasStainlessAppliances;
    private String earliestAvailableFrom;
}
