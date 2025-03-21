package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;

import java.util.List;

public interface ApartmentsFetcherClient {
    List<FloorPlanGroupDTO> fetchApartments(String communityId, int unitsPerFloor);
}
