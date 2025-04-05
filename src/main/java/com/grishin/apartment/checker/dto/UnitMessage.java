package com.grishin.apartment.checker.dto;

import com.grishin.apartment.checker.storage.entity.Unit;
import com.grishin.apartment.checker.storage.entity.UnitAmenity;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
public class UnitMessage {
    private String buildingNumber;
    private String unitMarketingName;
    private boolean isStudio;
    private Integer bedrooms;
    private Integer bathrooms;
    private Integer floor;
    private Integer price;
    private String floorPlanName;
    private List<String> amenityNames;
    private Date availableFrom;

    public static UnitMessage fromEntity(Unit unit) {
        return UnitMessage.builder()
                .buildingNumber(unit.getBuildingNumber())
                .unitMarketingName(unit.getUnitMarketingName())
                .isStudio(unit.getUnitIsStudio())
                .bedrooms(unit.getUnitIsStudio() ? null : unit.getFloorPlan().getFloorPlanBed())
                .bathrooms(unit.getUnitIsStudio() ? null : unit.getFloorPlan().getFloorPlanBath())
                .floor(unit.getUnitFloor())
                .price(unit.getUnitEarliestAvailable().getPrice())
                .floorPlanName(unit.getFloorPlan().getFloorPlanName())
                .amenityNames(unit.getAmenities().stream()
                        .map(UnitAmenity::getAmenityName)
                        .toList())
                .availableFrom(unit.getUnitEarliestAvailable().getAvailableDate())
                .build();
    }
}
