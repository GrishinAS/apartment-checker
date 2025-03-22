package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.storage.UserFilterPreferenceRepository;
import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserFilterService {

    private final UserFilterPreferenceRepository userFilterRepository;

    @Autowired
    public UserFilterService(UserFilterPreferenceRepository userFilterRepository) {
        this.userFilterRepository = userFilterRepository;
    }

    @Transactional
    public void saveUserFilters(Long userId, ApartmentFilter filters) {
        Optional<UserFilterPreference> existingPreference = userFilterRepository.findByUserId(userId);

        UserFilterPreference preference;
        if (existingPreference.isPresent()) {
            preference = existingPreference.get();
        } else {
            preference = new UserFilterPreference();
            preference.setUserId(userId);
            preference.setCreatedAt(LocalDateTime.now());
        }

        // Update with new filter values
        preference.setIsStudio(filters.getIsStudio());
        preference.setMinBedrooms(filters.getMinBedrooms());
        preference.setMaxBedrooms(filters.getMaxBedrooms());
        preference.setMinBathrooms(filters.getMinBathrooms());
        preference.setMaxBathrooms(filters.getMaxBathrooms());
        preference.setMinPrice(filters.getMinPrice());
        preference.setMaxPrice(filters.getMaxPrice());
        preference.setMinFloor(filters.getMinFloor());
        preference.setMaxFloor(filters.getMaxFloor());
        preference.setHasStainlessAppliances(filters.getHasStainlessAppliances());
        preference.setEarliestAvailableFrom(filters.getEarliestAvailableFrom());
//        preference.setBuildingNumber(filters.getBuildingNumber());
//        preference.setFloorplanName(filters.getFloorplanName());
        preference.setUpdatedAt(LocalDateTime.now());

        userFilterRepository.save(preference);
    }

    public ApartmentFilter getUserFilters(Long userId) {
        Optional<UserFilterPreference> preference = userFilterRepository.findByUserId(userId);

        if (preference.isPresent()) {
            UserFilterPreference userPref = preference.get();
            ApartmentFilter filterDTO = new ApartmentFilter();

            filterDTO.setIsStudio(userPref.getIsStudio());
            filterDTO.setMinBedrooms(userPref.getMinBedrooms());
            filterDTO.setMaxBedrooms(userPref.getMaxBedrooms());
            filterDTO.setMinBathrooms(userPref.getMinBathrooms());
            filterDTO.setMaxBathrooms(userPref.getMaxBathrooms());
            filterDTO.setMinPrice(userPref.getMinPrice());
            filterDTO.setMaxPrice(userPref.getMaxPrice());
            filterDTO.setMinFloor(userPref.getMinFloor());
            filterDTO.setMaxFloor(userPref.getMaxFloor());
            filterDTO.setHasStainlessAppliances(userPref.getHasStainlessAppliances());
            filterDTO.setEarliestAvailableFrom(userPref.getEarliestAvailableFrom());
//            filterDTO.setBuildingNumber(userPref.getBuildingNumber());
//            filterDTO.setFloorplanName(userPref.getFloorplanName());

            return filterDTO;
        }

        // Return empty filter if no preferences found
        return new ApartmentFilter();
    }

    @Transactional
    public void clearUserFilters(Long userId) {
        userFilterRepository.findByUserId(userId).ifPresent(userFilterRepository::delete);
    }

}
