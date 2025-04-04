package com.grishin.apartment.checker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.grishin.apartment.checker.TestDataProvider;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import com.grishin.apartment.checker.storage.UnitRepository;
import com.grishin.apartment.checker.telegram.MainBotController;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@SpringBootTest
@ExtendWith(MockitoExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Transactional
public class ApartmentCheckerTest {

    @Autowired
    private UnitRepository unitRepository;

    @Autowired
    private DataSyncService dataSyncService;

    @MockitoBean
    private IrvineCompanyClient client;

    @MockitoBean
    private MainBotController bot;

    @MockitoBean
    private TaskScheduler taskScheduler;

    @Autowired
    private ApartmentChecker apartmentChecker;


    @BeforeEach
    void setUp() {
        unitRepository.deleteAll();
    }

    @Test
    void testUpdateData_FindNewApartmentsAndNotify() throws Exception { //two appartments? good json with updated thats gonna reveal the db issue, test deletion
        List<FloorPlanGroupDTO> initialData = TestDataProvider.getInitialApartmentData();
        Mockito.when(client.fetchApartments(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(initialData);

        // Initial sync
        apartmentChecker.syncApartmentData();

        // Ensure data is loaded
        Assertions.assertEquals(initialData.get(0).getUnits().size(), unitRepository.count());

        // Second JSON load with new apartments
        List<FloorPlanGroupDTO> updatedData = TestDataProvider.getUpdatedApartmentDataWithNewUnits();
        Mockito.when(client.fetchApartments(Mockito.anyString(), Mockito.anyInt()))
                .thenReturn(updatedData);

        // Run check for new apartments
        apartmentChecker.checkForNewApartments();

        // Verify new apartments were added
        Assertions.assertTrue(unitRepository.count() > initialData.get(0).getUnits().size());

        // Verify notification was sent
        Mockito.verify(bot, Mockito.atLeastOnce()).sendMessage(Mockito.anyLong(), Mockito.anyString());
    }
}
