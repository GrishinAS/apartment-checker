package com.grishin.apartment.checker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApartmentSearchRequest {
    private String communityId;
    private int unitsPerFloor;
    private String env;
}
