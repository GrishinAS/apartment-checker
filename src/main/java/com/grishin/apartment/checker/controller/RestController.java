package com.grishin.apartment.checker.controller;

import com.grishin.apartment.checker.service.ApartmentChecker;
import com.grishin.apartment.checker.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

@org.springframework.web.bind.annotation.RestController
@RequiredArgsConstructor
public class RestController {

    private final ApartmentChecker apartmentChecker;
    private final DataSyncService dataSyncService;

    @GetMapping("/sync")
    public void syncData() {
        apartmentChecker.syncApartmentData();
    }

    @GetMapping("/forceScheduledCheck")
    public void checkForNewApartments() {
        apartmentChecker.checkForNewApartments();
    }
}
