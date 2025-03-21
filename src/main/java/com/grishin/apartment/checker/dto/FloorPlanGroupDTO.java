package com.grishin.apartment.checker.dto;

import lombok.Data;

import java.util.List;

@Data
public class FloorPlanGroupDTO {
    private String groupType;
    private List<String> floorPlanIds;
    private List<AptDTO> units;
    private List<String> unitIds;
}