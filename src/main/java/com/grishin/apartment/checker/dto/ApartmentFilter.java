package com.grishin.apartment.checker.dto;

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
}
