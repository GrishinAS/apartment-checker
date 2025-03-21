package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.AptDTO;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import com.grishin.apartment.checker.storage.ApartmentsRepository;
import com.grishin.apartment.checker.telegram.TelegramBot;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ApartmentChecker {
    private final TelegramBot bot;
    private final IrvineCompanyClient client;
    private final ApartmentsConfig apartmentsConfig;
    private final ApartmentsRepository repository;


    //@PostConstruct
    public void start() { //  @Scheduled(fixedRate = 30 * 60 * 1000) ??
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {
            scheduler.scheduleAtFixedRate(
                    this::checkForNewApartments,
                    0,
                    apartmentsConfig.getCheckIntervalMinutes(),
                    TimeUnit.MINUTES);
        }
    }

    private void checkForNewApartments() {
        List<FloorPlanGroupDTO> apartments = fetchAvailableApartments();
        Set<Long> existingIds = new HashSet<>(repository.findAllIds());
        // check for every new if already exists in db, add if not and remove from the set so we could find ones that dont exist anymore
//        apartments.stream()
//                .filter(apt -> !existingIds.contains(apt.getId()))
//                .forEach(apt -> {
//                    repository.save(apt);
//                    bot.sendMessage("New Apartment Available!\n" + apt);
//                });


    }

    private List<FloorPlanGroupDTO> fetchAvailableApartments() {
        CommunityConfig community = apartmentsConfig.getCommunities().stream()
                .filter(apt -> apt.getCommunityId().equals("Los Olivos")).findFirst().orElseThrow();
        return client.searchApartments(community.getCommunityId(), 10);
    }

    private boolean matchesFilters(AptDTO apt) {
        // TODO: Apply filters (e.g., price range, location, size)
        return apt.getUnitStartingPrice().getPrice() <= 1500;
    }
}

