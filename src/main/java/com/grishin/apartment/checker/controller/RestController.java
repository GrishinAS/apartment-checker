package com.grishin.apartment.checker.controller;

import com.grishin.apartment.checker.service.ApartmentChecker;
import com.grishin.apartment.checker.service.DataSyncService;
import com.grishin.apartment.checker.storage.entity.Unit;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@org.springframework.web.bind.annotation.RestController
@RequiredArgsConstructor
public class RestController {

    private final ApartmentChecker apartmentChecker;
    private final DataSyncService dataSyncService;

    @GetMapping("/sync")
    public void syncData() {
        apartmentChecker.syncApartmentData();
    }

    @GetMapping("/getCurrentApartments")
    public List<Unit> getCurrentApartments(Long userId) {
        return dataSyncService.findApartmentsForUser(userId);
    }

    @GetMapping("/forceScheduledCheck")
    public void checkForNewApartments() {
        apartmentChecker.checkForNewApartments();
    }
}
