package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.dto.ApartmentFilter;
import com.grishin.apartment.checker.storage.ApartmentSpecifications;
import com.grishin.apartment.checker.storage.UnitRepository;
import com.grishin.apartment.checker.storage.UserFilterPreferenceRepository;
import com.grishin.apartment.checker.storage.entity.Unit;
import com.grishin.apartment.checker.storage.entity.UserFilterPreference;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.grishin.apartment.checker.telegram.MainBotController.BOT_TIME_ZONE;

@Service
@Slf4j
public class UserFilterService {

    private final UserFilterPreferenceRepository userFilterRepository;
    private final UnitRepository unitRepository;

    @Autowired
    public UserFilterService(
            UserFilterPreferenceRepository userFilterRepository,
            UnitRepository unitRepository) {
        this.userFilterRepository = userFilterRepository;
        this.unitRepository = unitRepository;
    }

    @Transactional
    public List<Unit> findApartmentsWithFilters(ApartmentFilter filters) {
        Specification<Unit> spec = ApartmentSpecifications.filterBy(filters);
        List<Unit> units = unitRepository.findAll(spec);
        for (Unit unit : units) {
            Hibernate.initialize(unit.getAmenities());
        }
        return units;
    }

    @Transactional
    public void saveUserFilters(Long userId, String selectedCommunity, ApartmentFilter filters) {
        Optional<UserFilterPreference> existingPreference = userFilterRepository.findByUserId(userId);

        UserFilterPreference preference;
        if (existingPreference.isPresent()) {
            log.debug("Updating existing user filters for user {}", userId);
            preference = existingPreference.get();
        } else {
            log.debug("Creating new user filters for user {}", userId);
            preference = UserFilterPreference.builder()
                    .userId(userId)
                    .createdAt(LocalDateTime.now(BOT_TIME_ZONE))
                    .build();
        }

        preference.setIsStudio(filters.getIsStudio());
        preference.setMinBedrooms(filters.getMinBedrooms());
        preference.setMaxBedrooms(filters.getMaxBedrooms());
        preference.setMinBathrooms(filters.getMinBathrooms());
        preference.setMaxBathrooms(filters.getMaxBathrooms());
        preference.setMinPrice(filters.getMinPrice());
        preference.setMaxPrice(filters.getMaxPrice());
        preference.setMinFloor(filters.getMinFloor());
        preference.setMaxFloor(filters.getMaxFloor());
        preference.setAvailableFrom(new Date(filters.getMinDate().getTime()));
        preference.setAvailableUntil(new Date(filters.getMaxDate().getTime()));
        preference.setUpdatedAt(LocalDateTime.now(BOT_TIME_ZONE));
        preference.setSelectedCommunity(selectedCommunity);

        userFilterRepository.save(preference);
    }

    public ApartmentFilter getUserFilters(Long userId) {
        Optional<UserFilterPreference> preference = userFilterRepository.findByUserId(userId);

        if (preference.isPresent()) {
            UserFilterPreference userPref = preference.get();
            return ApartmentFilter.createFrom(userPref);
        }

        return new ApartmentFilter();
    }

    @Transactional
    public void clearUserFilters(Long userId) {
        userFilterRepository.findByUserId(userId).ifPresent(userFilterRepository::delete);
    }

}
