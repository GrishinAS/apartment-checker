package com.grishin.apartment.checker;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

public class TestDataProvider {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static List<FloorPlanGroupDTO> getInitialApartmentData() throws IOException {
        return parseFile(new TypeReference<>() {}, "apartment-setup.json");
    }

    public static List<FloorPlanGroupDTO> getUpdatedApartmentDataWithNewUnits() throws IOException {
        return parseFile(new TypeReference<>() {}, "apartment-update.json");
    }

    public static List<FloorPlanGroupDTO> getUpdatedApartmentDataWithRemovedUnits() throws IOException {
        return parseFile(new TypeReference<>() {}, "apartment-delete.json");
    }

    public static List<FloorPlanGroupDTO> getSampleUnits() throws IOException {
        return parseFile(new TypeReference<>() {}, "irvine-apartment-sample.json");
    }

    private static List<FloorPlanGroupDTO> parseFile(TypeReference<List<FloorPlanGroupDTO>> valueTypeRef, String json) throws IOException {
        return objectMapper.readValue(
                new ClassPathResource(json).getInputStream(),
                valueTypeRef);
    }
}
