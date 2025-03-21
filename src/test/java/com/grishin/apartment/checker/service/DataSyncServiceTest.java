package com.grishin.apartment.checker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.dto.AptDTO;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import com.grishin.apartment.checker.storage.*;
import com.grishin.apartment.checker.storage.entity.FloorPlanGroup;
import com.grishin.apartment.checker.storage.entity.Unit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@Import(DataSyncService.class)
public class DataSyncServiceTest {

    @MockitoBean
    ApartmentsFetcherClient client;
    @MockitoBean
    ApartmentsConfig config;
    @MockitoBean
    private TaskScheduler taskScheduler;

    @Autowired
    private DataSyncService apartmentService;

    @Autowired
    private FloorPlanGroupRepository floorPlanGroupRepository;

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private FloorPlanRepository floorPlanRepository;

    @Autowired
    private CommunityRepository communityRepository;

    @Autowired
    private UnitAmenityRepository unitAmenityRepository;

    @Autowired
    private LeasePriceRepository leasePriceRepository;

    @Test
    public void testProcessApartmentData() throws IOException {
        // Load test data from JSON file
        ObjectMapper objectMapper = new ObjectMapper();
        String path = "irvine-apartment-sample.json";
        List<FloorPlanGroupDTO> apartmentDataList = objectMapper.readValue(
                new ClassPathResource(path).getInputStream(),
                new TypeReference<List<FloorPlanGroupDTO>>() {});

        // Verify test data is loaded correctly
        assertNotNull(apartmentDataList);
        assertFalse(apartmentDataList.isEmpty());

        // Process the data
        apartmentService.processApartmentData(apartmentDataList);

        // Verify floor plan groups were created
        List<FloorPlanGroup> groups = floorPlanGroupRepository.findAll();
        assertFalse(groups.isEmpty());

        // Verify each group has expected data
        for (FloorPlanGroupDTO groupDTO : apartmentDataList) {
            FloorPlanGroup group = floorPlanGroupRepository.findByGroupType(groupDTO.getGroupType());
            assertNotNull(group, "Group with type " + groupDTO.getGroupType() + " should exist");

            // Check if units were created
            if (groupDTO.getUnits() != null) {
                for (AptDTO aptDTO : groupDTO.getUnits()) {
                    Optional<Unit> unitOpt = unitRepository.findById(aptDTO.getObjectID());
                    assertTrue(unitOpt.isPresent(), "Unit " + aptDTO.getObjectID() + " should exist");

                    Unit unit = unitOpt.get();
                    assertEquals(aptDTO.getUnitMarketingName(), unit.getUnitMarketingName()); // unitID is not unique
                    assertTrue(unit.getGroups().contains(group), "Unit should be associated with the group");

                    // Verify floor plan was created
                    assertNotNull(unit.getFloorPlan());
                    assertEquals(aptDTO.getFloorplanUniqueID(), unit.getFloorPlan().getFloorPlanUniqueId());

                    // Verify community was created
                    assertNotNull(unit.getCommunity());
                    assertEquals(aptDTO.getCommunityIDAEM(), unit.getCommunity().getId());

                    // Verify amenities if any
                    if (aptDTO.getUnitAmenities() != null && !aptDTO.getUnitAmenities().isEmpty()) {
                        assertFalse(unit.getAmenities().isEmpty());
                        assertEquals(aptDTO.getUnitAmenities().size(), unit.getAmenities().size());
                    }

                    // Verify lease prices
                    assertNotNull(unit.getLeasePrices());
                    int expectedPriceCount = 0;
                    if (aptDTO.getUnitEarliestAvailable() != null) expectedPriceCount++;
                    if (aptDTO.getUnitStartingPrice() != null) expectedPriceCount++;
                    if (aptDTO.getUnitLeasePrice() != null) expectedPriceCount += new HashSet<>(aptDTO.getUnitLeasePrice()).size();

                    assertEquals(expectedPriceCount, unit.getLeasePrices().size());
                }
            }

            // Check if unitIds were properly linked
            if (groupDTO.getUnitIds() != null) {
                for (String unitId : groupDTO.getUnitIds()) {
                    Optional<Unit> unitOpt = unitRepository.findById(unitId);
                    if (unitOpt.isPresent()) {
                        Unit unit = unitOpt.get();
                        assertTrue(unit.getGroups().contains(group),
                                "Unit " + unitId + " should be associated with the group");
                    }
                }
            }
        }
    }
}