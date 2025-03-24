package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.dto.AptDTO;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import com.grishin.apartment.checker.storage.ApartmentSpecifications;
import com.grishin.apartment.checker.storage.UnitRepository;
import com.grishin.apartment.checker.storage.UserFilterPreferenceRepository;
import com.grishin.apartment.checker.storage.entity.Unit;
import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import com.grishin.apartment.checker.telegram.MainBotController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ApartmentChecker {
    private final IrvineCompanyClient client;
    private final ApartmentsConfig apartmentsConfig;
    private final UserFilterService userFilterService;
    private final UnitRepository unitRepository;
    private final DataSyncService dataSyncService;
    private final MainBotController bot;
    private final UserFilterPreferenceRepository userFilterPreferenceRepository;


    @Scheduled(fixedRateString = "${apartments.checkInterval}",  initialDelayString = "${apartments.checkInterval}", timeUnit = TimeUnit.MINUTES)
    public void checkForNewApartments() {
        log.info("Checking for new apartments");
        try {
            Set<String> existingUnitIds = unitRepository.findAll()
                    .stream().map(Unit::getObjectId).collect(Collectors.toSet());
            log.info("Found {} existing units", existingUnitIds.size());

            List<String> communities = apartmentsConfig.getCommunities()
                    .stream().map(CommunityConfig::getName).toList();

            for (String community : communities) {
                checkAndNotifyCommunity(community, existingUnitIds);
            }
        } catch (Exception e) {
            log.error("Error during apartment check", e);
        }
    }

    private void checkAndNotifyCommunity(String community, Set<String> existingUnitIds) {
        List<Long> usersThatSelectedCommunity = userFilterPreferenceRepository.findBySelectedCommunity(community)
                .stream().map(UserFilterPreference::getUserId).toList();
        List<FloorPlanGroupDTO> newApartmentData = fetchAvailableApartments(community);
        Set<AptDTO> newApartments = newApartmentData.stream()
                .flatMap(group -> group.getUnits().stream())
                .filter(unit -> !existingUnitIds.contains(unit.getObjectID())).collect(Collectors.toSet());
        log.info("Found {} new apartments in community {}", newApartments.size(), community);
        if (newApartments.size() > 3) {
            log.warn("Found too much new apartments: {}", newApartments.size());
            return;
        }
        for (Long userId : usersThatSelectedCommunity) {
            for (AptDTO newApartment : newApartments) {
                try {
                    alertNewUnit(newApartment, userId);
                } catch (TelegramApiException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    //@PostConstruct
    public void syncApartmentData() {
        log.info("Starting apartment data synchronization");
        try {
            List<String> communities = apartmentsConfig.getCommunities()
                    .stream().map(CommunityConfig::getName).toList();
            for (String communityName : communities) {
                List<FloorPlanGroupDTO> apartmentData = fetchAvailableApartments(communityName);

                dataSyncService.processApartmentData(apartmentData);
                log.info("Synchronized apartment data for community {}", communityName);
            }
            log.info("Apartment data synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during apartment data synchronization", e);
        }
    }

    public List<Unit> findApartmentsWithFilters(ApartmentFilter filters) {
        Specification<Unit> spec = ApartmentSpecifications.filterBy(filters);
        return unitRepository.findAll(spec);
    }

    public List<Unit> findApartmentsForUser(Long userId) {
        ApartmentFilter filters = userFilterService.getUserFilters(userId);
        return findApartmentsWithFilters(filters);
    }

    private void alertNewUnit(AptDTO unit, Long userId) throws TelegramApiException {
        StringBuilder message = new StringBuilder();
        message.append("*New Apartment Available!*\n\n");

        if (unit.isUnitIsStudio()) {
            message.append("Studio");
        } else {
            message.append("Bedrooms: ").append(unit.getFloorplanBed()).append("\n");
            message.append("Bathrooms: ").append(unit.getFloorplanBath()).append("\n");
        }
        message.append("Building Number: ").append(unit.getBuildingNumber()).append("\n");
        message.append("Floor: ").append(unit.getUnitFloor()).append("\n");
        message.append("Price: $").append(unit.getUnitEarliestAvailable().getPrice()).append("\n");
        message.append("Floorplan: ").append(unit.getFloorplanName()).append("\n");
        message.append("Stainless Steel Appliances: ").append(unit.getUnitAmenities().contains("Stainless Steel Appliances") ? "Yes" : "No").append("\n");
        message.append("Available From: ").append(unit.getUnitEarliestAvailable().getDate());

        bot.sendMessage(userId, message.toString());
    }

    private List<FloorPlanGroupDTO> fetchAvailableApartments(String communityName) {
        CommunityConfig community = apartmentsConfig.getCommunities().stream()
                .filter(c -> c.getName().equals(communityName)).findFirst().orElseThrow();
        return client.fetchApartments(community.getCommunityId(), 10);
    }
}

