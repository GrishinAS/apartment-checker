package com.grishin.apartment.checker;

import com.grishin.apartment.checker.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class Controller {

    private final DataSyncService dataSyncService;

    @GetMapping("/sync")
    public void syncData() {
        dataSyncService.syncApartmentData();
    }
}
