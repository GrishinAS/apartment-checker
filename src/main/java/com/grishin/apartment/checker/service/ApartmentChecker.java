package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.dto.AptDTO;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import com.grishin.apartment.checker.dto.UnitMessage;
import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import com.grishin.apartment.checker.telegram.KeyboardUtils;
import com.grishin.apartment.checker.telegram.MainBotController;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApartmentChecker {
    private final IrvineCompanyClient client;
    private final ApartmentsConfig apartmentsConfig;
    private final DataSyncService dataSyncService;
    private final MainBotController bot;



    @Scheduled(fixedRateString = "${apartments.checkInterval}",  initialDelayString = "${apartments.checkInterval}", timeUnit = TimeUnit.MINUTES)
    public void checkForNewApartments() {
        log.info("Checking for new apartments");
        try {
            Set<String> existingUnitIds = dataSyncService.getExistingUnits();
            log.info("Found {} existing units", existingUnitIds.size());

            List<CommunityConfig> communities = apartmentsConfig.getCommunities();

            Map<String, Set<AptDTO>> newApartmentsPerCommunity = new HashMap<>();
            for (CommunityConfig community : communities) {
                List<FloorPlanGroupDTO> newApartmentDataForCommunity = fetchAvailableApartments(community.getCommunityId());

                Set<AptDTO> newApartmentsForCommunity = newApartmentDataForCommunity.stream()
                        .flatMap(group -> group.getUnits().stream())
                        .filter(unit -> !existingUnitIds.contains(unit.getObjectID())).collect(Collectors.toSet());

                newApartmentsPerCommunity.put(community.getName(), newApartmentsForCommunity);

                dataSyncService.processApartmentData(newApartmentDataForCommunity, community.getCommunityId());
            }

            for (CommunityConfig community : communities) {

                List<UserFilterPreference> usersThatSelectedCommunity = dataSyncService.findUsersBySelectedCommunity(community);

                for (UserFilterPreference userPref : usersThatSelectedCommunity) {
                    ApartmentFilter userFilters = ApartmentFilter.createFrom(userPref);

                    List<String> newApartmentsIdsForCommunity = newApartmentsPerCommunity.get(community.getName()).stream().map(AptDTO::getObjectID).toList();

                    List<UnitMessage> filteredNewUnits = dataSyncService.findApartmentsByIdsWithFilters(userFilters, newApartmentsIdsForCommunity);

                    for (UnitMessage newApartment : filteredNewUnits) {
                        try {
                            alertNewUnit(newApartment, userPref.getUserId());
                        } catch (TelegramApiException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error during apartment check", e);
        }
    }


    @PostConstruct
    public void syncApartmentData() {
        log.info("Starting apartment data synchronization");
        try {
            List<CommunityConfig> communities = apartmentsConfig.getCommunities();
            for (CommunityConfig community: communities) {
                List<FloorPlanGroupDTO> apartmentData = fetchAvailableApartments(community.getCommunityId());

                dataSyncService.processApartmentData(apartmentData, community.getCommunityId());
                log.info("Synchronized apartment data for community {}", community.getName());
            }
            log.info("Apartment data synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during apartment data synchronization", e);
        }
    }



    private void alertNewUnit(UnitMessage unit, Long userId) throws TelegramApiException {
        String message = KeyboardUtils.alertAvailableUnitMessage(unit);
        bot.sendMessage(userId, message);
    }

    private List<FloorPlanGroupDTO> fetchAvailableApartments(String communityId) {
        return client.fetchApartments(communityId, 10);
    }
}

