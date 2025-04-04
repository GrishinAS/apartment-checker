package com.grishin.apartment.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

public class TestDataProvider {

    public static List<FloorPlanGroupDTO> getInitialApartmentData() throws IOException {
        return parseFile(new TypeReference<>() {}, "apartment-setup.json");
    }

    public static List<FloorPlanGroupDTO> getUpdatedApartmentDataWithNewUnits() throws IOException {
        return parseFile(new TypeReference<>() {}, "apartment-update.json");
    }

    private static List<FloorPlanGroupDTO> parseFile(TypeReference<List<FloorPlanGroupDTO>> valueTypeRef, String json) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.readValue(
                new ClassPathResource(json).getInputStream(),
                valueTypeRef);
    }
}
