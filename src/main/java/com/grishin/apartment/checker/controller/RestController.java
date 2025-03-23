package com.grishin.apartment.checker.controller;

import com.grishin.apartment.checker.service.ApartmentChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;

@org.springframework.web.bind.annotation.RestController
@RequiredArgsConstructor
public class RestController {

    private final ApartmentChecker apartmentChecker;

    @GetMapping("/sync")
    public void syncData() {
        apartmentChecker.syncApartmentData();
    }
}
