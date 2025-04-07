package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.TestDataProvider;
import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import com.grishin.apartment.checker.storage.FloorPlanGroupRepository;
import com.grishin.apartment.checker.storage.UnitAmenityRepository;
import com.grishin.apartment.checker.storage.UnitRepository;
import com.grishin.apartment.checker.storage.UserFilterPreferenceRepository;
import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import com.grishin.apartment.checker.telegram.MainBotController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.mockito.Mockito.*;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
public class ApartmentCheckerTest {

    @Autowired
    private UnitRepository unitRepository;
    @Autowired
    private FloorPlanGroupRepository floorPlanGroupRepository;
    @Autowired
    private UnitAmenityRepository unitAmenityRepository;

    @Autowired
    private DataSyncService dataSyncService;
    @Autowired
    private ApartmentChecker apartmentChecker;

    @MockitoBean
    private IrvineCompanyClient client;
    @MockitoBean
    private MainBotController bot;
    @MockitoBean
    private TaskScheduler taskScheduler;
    @MockitoBean
    private UserFilterPreferenceRepository userFilterRepository;
    @MockitoBean
    private UserFilterService userFilterService;
    @MockitoBean
    private ApartmentsConfig apartmentsConfig;


    @BeforeEach
    @Transactional
    void setUp() {
        floorPlanGroupRepository.deleteAll();
        unitRepository.deleteAll();
        unitAmenityRepository.deleteAll();
    }

    @Test
    void testUpdateData_FindNewApartmentsAndNotify() throws Exception { //two appartments?
        // Mocks setup
        CommunityConfig communityConfig = new CommunityConfig("Mock ID", "Promenade");
        when(apartmentsConfig.getCommunities())
                .thenReturn(List.of(communityConfig));
        Long userId = 11L;
        UserFilterPreference userFilterPreference = UserFilterPreference.builder()
                .userId(userId)
                .selectedCommunity(communityConfig.getName())
                .build();
        when(userFilterRepository.findBySelectedCommunity(matches(communityConfig.getName())))
                .thenReturn(List.of(userFilterPreference));
        List<FloorPlanGroupDTO> initialData = TestDataProvider.getInitialApartmentData();
        when(client.fetchApartments(matches(communityConfig.getCommunityId()), anyInt()))
                .thenReturn(initialData);

        // Initial sync
        apartmentChecker.syncApartmentData();

        // Ensure data is loaded
        long initialUnitsCount = initialData.stream().mapToLong(x -> x.getUnits().size()).sum();
        Assertions.assertEquals(initialUnitsCount, unitRepository.count());

        // Second JSON load with new apartments
        List<FloorPlanGroupDTO> updatedData = TestDataProvider.getUpdatedApartmentDataWithNewUnits();
        when(client.fetchApartments(anyString(), anyInt()))
                .thenReturn(updatedData);

        // Run check for new apartments
        apartmentChecker.checkForNewApartments();

        // Verify new apartments were added
        int unitsAdded = 6;
        Assertions.assertEquals(initialUnitsCount + unitsAdded, unitRepository.count());

        // Verify notification was sent
        verify(bot, times(unitsAdded)).sendMessage(eq(userId), anyString());
    }

    @Test
    void testDeleteData_DeleteOldApartments() throws Exception {
        // Mocks setup
        CommunityConfig communityConfig = new CommunityConfig("11584d39-2644-4b8e-8548-7c2a126c0570", "Promenade");
        when(apartmentsConfig.getCommunities())
                .thenReturn(List.of(communityConfig));
        Long userId = 11L;
        UserFilterPreference userFilterPreference = UserFilterPreference.builder().
                userId(userId)
                .selectedCommunity(communityConfig.getName()).build();
        when(userFilterRepository.findBySelectedCommunity(matches(communityConfig.getName())))
                .thenReturn(List.of(userFilterPreference));
        List<FloorPlanGroupDTO> initialData = TestDataProvider.getInitialApartmentData();
        when(client.fetchApartments(matches(communityConfig.getCommunityId()), anyInt()))
                .thenReturn(initialData);

        // Initial sync
        apartmentChecker.syncApartmentData();

        // Ensure data is loaded
        long initialUnitsCount = initialData.stream().mapToLong(x -> x.getUnits().size()).sum();
        Assertions.assertEquals(initialUnitsCount, unitRepository.count());

        // Second JSON load with removed apartments
        List<FloorPlanGroupDTO> updatedData = TestDataProvider.getUpdatedApartmentDataWithRemovedUnits();
        when(client.fetchApartments(matches(communityConfig.getCommunityId()), anyInt()))
                .thenReturn(updatedData);

        // Run check for new apartments
        apartmentChecker.checkForNewApartments();

        // Verify new apartments were added
        int unitsDeleted = 3;
        Assertions.assertEquals(initialUnitsCount - unitsDeleted, unitRepository.count());

        // Verify notification was sent
        verify(bot, never()).sendMessage(anyLong(), anyString());
    }
}
