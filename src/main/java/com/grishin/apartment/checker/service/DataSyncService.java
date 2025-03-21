package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.config.ApartmentsConfig;
import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.AptDTO;
import com.grishin.apartment.checker.dto.FloorPlanGroupDTO;
import com.grishin.apartment.checker.dto.LeaseTermDTO;
import com.grishin.apartment.checker.storage.*;
import com.grishin.apartment.checker.storage.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncService {

    private final CommunityRepository communityRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final FloorPlanGroupRepository floorPlanGroupRepository;
    private final UnitRepository unitRepository;
    private final UnitAmenityRepository unitAmenityRepository;
    private final LeasePriceRepository leasePriceRepository;
    private final ApartmentsFetcherClient client;
    private final ApartmentsConfig apartmentsConfig;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
    
    @Scheduled(fixedRateString = "${apartments.checkInterval}", initialDelay = 0)
    public void syncApartmentData() {
        log.info("Starting apartment data synchronization");
        try {
            List<FloorPlanGroupDTO> apartmentData = fetchAvailableApartments();

            processApartmentData(apartmentData);

            log.info("Apartment data synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during apartment data synchronization", e);
        }
    }

    @Cacheable("apartments") // remove after development
    private List<FloorPlanGroupDTO> fetchAvailableApartments() {
        CommunityConfig community = apartmentsConfig.getCommunities().stream()
                .filter(apt -> apt.getName().equals("Los Olivos")).findFirst().orElseThrow();
        return client.fetchApartments(community.getCommunityId(), 10);
    }

    @Transactional
    public void processApartmentData(List<FloorPlanGroupDTO> apartmentDataList) {
        
        Set<String> processedUnitIds = new HashSet<>();

        for (FloorPlanGroupDTO apartmentData : apartmentDataList) {
            
            FloorPlanGroup group = getOrCreateFloorPlanGroup(apartmentData.getGroupType());
            floorPlanGroupRepository.saveAndFlush(group);

            
            if (apartmentData.getUnits() != null) {
                for (AptDTO unit : apartmentData.getUnits()) {
                    processUnit(unit, group);
                    processedUnitIds.add(unit.getObjectID());
                }
            }

            if (apartmentData.getUnitIds() != null) {
                for (String unitId : apartmentData.getUnitIds()) {
                    Optional<Unit> unitOpt = unitRepository.findById(unitId);
                    unitOpt.ifPresent(unit -> {
                        // Check if the unit is already in the group to avoid duplicates
                        boolean unitAlreadyInGroup = group.getUnits().stream()
                                .anyMatch(u -> u.getObjectId().equals(unit.getObjectId()));

                        if (!unitAlreadyInGroup) {
                            group.getUnits().add(unit);
                            unit.getGroups().add(group);
                            unitRepository.save(unit);
                        }
                    });
                }
            }
            
            floorPlanGroupRepository.save(group);
        }

        handleRemovedUnits(processedUnitIds);
    }

    private FloorPlanGroup getOrCreateFloorPlanGroup(String groupType) {
        FloorPlanGroup group = floorPlanGroupRepository.findByGroupType(groupType);
        if (group == null) {
            group = new FloorPlanGroup();
            group.setGroupType(groupType);
        }
        return group;
    }

    private void processUnit(AptDTO aptDto, FloorPlanGroup group) {
        Community community = getOrCreateCommunity(aptDto);
        
        FloorPlan floorPlan = getOrCreateFloorPlan(aptDto);
        
        Unit unit = getOrCreateUnit(aptDto, community, floorPlan);

        addUnitToGroup(unit, group);
        addFloorPlanToGroup(floorPlan, group);
        
        processUnitAmenities(unit, aptDto.getUnitAmenities());
        
        processLeasePrices(unit, aptDto);
        
        unitRepository.save(unit);
    }

    private void addUnitToGroup(Unit unit, FloorPlanGroup group) {
        boolean unitAlreadyInGroup = group.getUnits().stream()
                .anyMatch(u -> u.getObjectId().equals(unit.getObjectId()));

        if (!unitAlreadyInGroup) {
            group.getUnits().add(unit);
            unit.getGroups().add(group);
        }
    }

    private void addFloorPlanToGroup(FloorPlan floorPlan, FloorPlanGroup group) {
        if (!group.getFloorPlans().contains(floorPlan)) {
            group.getFloorPlans().add(floorPlan);
            floorPlan.getGroups().add(group);
        }
    }

    private Community getOrCreateCommunity(AptDTO aptDTO) {
        Community community = communityRepository.findById(aptDTO.getCommunityIDAEM()).orElse(new Community());

        community.setId(aptDTO.getCommunityIDAEM());
        community.setMarketingName(aptDTO.getCommunityMarketingName());
        community.setPropertyId(aptDTO.getPropertyID());
        community.setPropertyAddress(aptDTO.getPropertyAddress());
        community.setPropertyZip(aptDTO.getPropertyZip());

        return communityRepository.save(community);
    }

    private FloorPlan getOrCreateFloorPlan(AptDTO aptDTO) {
        FloorPlan floorPlan = floorPlanRepository.findById(aptDTO.getFloorplanUniqueID())
                .orElse(new FloorPlan());

        floorPlan.setFloorPlanUniqueId(aptDTO.getFloorplanUniqueID());
        floorPlan.setFloorPlanId(aptDTO.getFloorplanID());
        floorPlan.setPropertyId(aptDTO.getPropertyID());
        floorPlan.setFloorPlanName(aptDTO.getFloorplanName());
        floorPlan.setFloorPlanCrmId(aptDTO.getFloorplanCRMID());
        floorPlan.setFloorPlanPath(aptDTO.getFloorplanPath());
        floorPlan.setFloorPlanSqft(aptDTO.getFloorplanSqFt());
        floorPlan.setFloorPlanBed(aptDTO.getFloorplanBed());
        floorPlan.setFloorPlanBath(aptDTO.getFloorplanBath());
        floorPlan.setFloorPlanDeposit(aptDTO.getFloorplanDeposit());

        return floorPlanRepository.save(floorPlan);
    }

    private Unit getOrCreateUnit(AptDTO aptDTO, Community community, FloorPlan floorPlan) {
        Unit unit = unitRepository.findById(aptDTO.getObjectID()).orElse(new Unit());

        unit.setUnitId(aptDTO.getUnitID());
        unit.setUnitMarketingName(aptDTO.getUnitMarketingName());
        unit.setUnitCrmId(aptDTO.getUnitCRMID());
        unit.setUnitFloor(aptDTO.getUnitFloor());
        unit.setUnitSqft(aptDTO.getUnitSqFt());
        unit.setUnitTypeCode(aptDTO.getUnitTypeCode());
        unit.setUnitTypeName(aptDTO.getUnitTypeName());
        unit.setBuildingNumber(aptDTO.getBuildingNumber());
        unit.setUnitIsStudio(aptDTO.isUnitIsStudio());
        unit.setUnitHasDiscount(aptDTO.isUnitHasDiscount());
        unit.setFeaturedAmenity(aptDTO.getFeaturedAmenity());
        unit.setObjectId(aptDTO.getObjectID());
        unit.setCommunity(community);
        unit.setFloorPlan(floorPlan);

        return unit;
    }

    private void processUnitAmenities(Unit unit, List<String> amenityNames) {
        if (amenityNames == null || amenityNames.isEmpty()) {
            return;
        }

        unit.getAmenities().clear();

        for (String amenityName : amenityNames) {
            UnitAmenity amenity = unitAmenityRepository.findByAmenityName(amenityName)
                    .orElseGet(() -> {
                        UnitAmenity newAmenity = new UnitAmenity();
                        newAmenity.setAmenityName(amenityName);
                        return unitAmenityRepository.save(newAmenity);
                    });
            unit.getAmenities().add(amenity);
        }
    }

    private void processLeasePrices(Unit unit, AptDTO aptDTO) {
        
        leasePriceRepository.deleteByUnitUnitId(unit.getObjectId());
        unit.getLeasePrices().clear();
        
        if (aptDTO.getUnitEarliestAvailable() != null) {
            LeasePrice earliestPrice = createLeasePrice(aptDTO.getUnitEarliestAvailable(), unit);
            earliestPrice.setIsEarliestAvailable(true);
            earliestPrice.setIsStartingPrice(false);
            unit.getLeasePrices().add(earliestPrice);
        }
        
        if (aptDTO.getUnitStartingPrice() != null) {
            LeasePrice startingPrice = createLeasePrice(aptDTO.getUnitStartingPrice(), unit);
            startingPrice.setIsEarliestAvailable(false);
            startingPrice.setIsStartingPrice(true);
            unit.getLeasePrices().add(startingPrice);
        }

        if (aptDTO.getUnitLeasePrice() != null) {
            for (LeaseTermDTO LeaseTermDTO : aptDTO.getUnitLeasePrice()) {
                LeasePrice leasePrice = createLeasePrice(LeaseTermDTO, unit);
                leasePrice.setIsEarliestAvailable(false);
                leasePrice.setIsStartingPrice(false);
                unit.getLeasePrices().add(leasePrice);
            }
        }
    }

    private LeasePrice createLeasePrice(LeaseTermDTO leaseTermDTO, Unit unit) {
        LeasePrice leasePrice = new LeasePrice();
        leasePrice.setPrice(leaseTermDTO.getPrice());
        leasePrice.setTerm(leaseTermDTO.getTerm());
        leasePrice.setDateTimestamp(leaseTermDTO.getDateTimeStamp());
        leasePrice.setUnit(unit);

        try {
            leasePrice.setAvailableDate(DATE_FORMAT.parse(leaseTermDTO.getDate()));
        } catch (ParseException e) {
            log.warn("Could not parse date: " + leaseTermDTO.getDate(), e);
        }

        return leasePrice;
    }

    private void handleRemovedUnits(Set<String> processedUnitIds) {
        List<Unit> allUnits = unitRepository.findAll();
        List<Unit> removedUnits = allUnits.stream()
                .filter(unit -> !processedUnitIds.contains(unit.getObjectId()))
                .toList();

        if (!removedUnits.isEmpty()) {
            log.info("Detected {} removed units", removedUnits.size());
            // Option 1: Delete removed units
            // unitRepository.deleteAll(removedUnits);

            // Option 2: Flag them as inactive
            // for (Unit unit : removedUnits) {
            //     unit.setActive(false);
            //     unitRepository.save(unit);
            // }
        }
    }
}
