package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.dto.ApartmentSearchRequest;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@Service
public class IrvineCompanyClient implements ApartmentsFetcherClient {
    private final RestTemplate restTemplate;
    private final ApartmentsConfig apartmentsConfig;

    @Autowired
    public IrvineCompanyClient(RestTemplate restTemplate, ApartmentsConfig apartmentsConfig) {
        this.restTemplate = restTemplate;
        this.apartmentsConfig = apartmentsConfig;
    }

    @Override
    public List<FloorPlanGroupDTO> fetchApartments(String communityId, int unitsPerFloor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ApartmentSearchRequest requestBody = ApartmentSearchRequest.builder()
                .communityId(communityId)
                .unitsPerFloor(unitsPerFloor)
                .env("prod")
                .build();

        HttpEntity<ApartmentSearchRequest> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<FloorPlanGroupDTO[]> response = restTemplate.postForEntity(
                apartmentsConfig.getUrl(),
                requestEntity,
                FloorPlanGroupDTO[].class
        );

        return Arrays.asList(Objects.requireNonNull(response.getBody(), "Empty response"));
    }
}
