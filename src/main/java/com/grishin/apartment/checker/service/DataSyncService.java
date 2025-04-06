package com.grishin.apartment.checker.service;

import com.grishin.apartment.checker.config.CommunityConfig;
import com.grishin.apartment.checker.dto.*;
import com.grishin.apartment.checker.storage.*;
import com.grishin.apartment.checker.storage.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncService {

    private final CommunityRepository communityRepository;
    private final FloorPlanRepository floorPlanRepository;
    private final FloorPlanGroupRepository floorPlanGroupRepository;
    private final UnitRepository unitRepository;
    private final UnitAmenityRepository unitAmenityRepository;
    private final UserFilterService userFilterService;
    private final UserFilterPreferenceRepository userFilterPreferenceRepository;
    @PersistenceContext
    private EntityManager entityManager;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    @Transactional
    public void processApartmentData(List<FloorPlanGroupDTO> apartmentDataList, String communityId) {
        
        Set<String> processedUnitIds = new HashSet<>();
        int counter = 1;
        for (FloorPlanGroupDTO apartmentData : apartmentDataList) {
            log.debug("Processing FloorPlanGroupDTO #{} type {}", counter++, apartmentData.getGroupType());
            
            FloorPlanGroup group = getOrCreateFloorPlanGroup(apartmentData.getGroupType());
            floorPlanGroupRepository.saveAndFlush(group);

            
            if (apartmentData.getUnits() != null) {
                for (AptDTO apt : apartmentData.getUnits()) {
                    processUnit(apt, group);
                    processedUnitIds.add(apt.getObjectID());
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

        handleRemovedUnits(processedUnitIds, communityId);
    }

    public Set<String> getExistingUnits() {
        return unitRepository.findAll()
                .stream().map(Unit::getObjectId).collect(Collectors.toSet());
    }

    public List<UserFilterPreference> findUsersBySelectedCommunity(CommunityConfig community) {
        return userFilterPreferenceRepository.findBySelectedCommunity(community.getName());
    }

    public List<Unit> findApartmentsWithFilters(ApartmentFilter filters) {
        Specification<Unit> spec = ApartmentSpecifications.filterBy(filters);
        return unitRepository.findAll(spec);
    }

    @Transactional
    public List<UnitMessage> findApartmentsByIdsWithFilters(ApartmentFilter filters, List<String> ids) {
        Specification<Unit> spec = ApartmentSpecifications.filterBy(filters, ids);
        List<Unit> unitEntities = unitRepository.findAll(spec);
        return unitEntities.stream().map(UnitMessage::fromEntity).toList();
    }

    public List<Unit> findApartmentsForUser(Long userId) {
        ApartmentFilter filters = userFilterService.getUserFilters(userId);
        return findApartmentsWithFilters(filters);
    }

    private FloorPlanGroup getOrCreateFloorPlanGroup(String groupType) {
        FloorPlanGroup group = floorPlanGroupRepository.findByGroupType(groupType);
        if (group == null) {
            log.debug("Creating new FloorPlanGroup: {}", groupType);
            group = new FloorPlanGroup();
            group.setGroupType(groupType);
        }
        log.debug("Return FloorPlanGroup: {}", group.getGroupId());
        return group;
    }

    private void processUnit(AptDTO aptDto, FloorPlanGroup group) {
        log.debug("Processing unit: {}", aptDto.getUnitID());
        Community community = getOrCreateCommunity(aptDto);
        
        FloorPlan floorPlan = getOrCreateFloorPlan(aptDto);
        
        Unit unit = getOrCreateUnit(aptDto, community, floorPlan);

        addUnitToGroup(unit, group);
        addFloorPlanToGroup(floorPlan, group);
        
        processUnitAmenities(unit, aptDto.getUnitAmenities());
        
        processLeasePrice(unit, aptDto.getUnitEarliestAvailable());

    }

    private void addUnitToGroup(Unit unit, FloorPlanGroup group) {
        boolean unitAlreadyInGroup = group.getUnits().stream()
                .anyMatch(u -> u.getObjectId().equals(unit.getObjectId()));

        if (!unitAlreadyInGroup) {
            log.debug("Adding unit {} to group: {}", unit.getObjectId(), group.getGroupId());
            group.getUnits().add(unit);
            unit.getGroups().add(group);
        }
    }

    private void addFloorPlanToGroup(FloorPlan floorPlan, FloorPlanGroup group) {
        if (!group.getFloorPlans().contains(floorPlan)) {
            log.debug("Adding floor plan {} to group: {}", floorPlan.getFloorPlanId(), group.getGroupId());
            group.getFloorPlans().add(floorPlan);
            floorPlan.getGroups().add(group);
        }
    }

    private Community getOrCreateCommunity(AptDTO aptDTO) {
        Optional<Community> comOpt = communityRepository.findById(aptDTO.getCommunityIDAEM());
        if (comOpt.isPresent()) {
            log.debug("Community is present {}", comOpt.get().getId());
            return comOpt.get();
        }
        
        Community community = new Community();

        community.setId(aptDTO.getCommunityIDAEM());
        community.setMarketingName(aptDTO.getCommunityMarketingName());
        community.setPropertyId(aptDTO.getPropertyID());
        community.setPropertyAddress(aptDTO.getPropertyAddress());
        community.setPropertyZip(aptDTO.getPropertyZip());
        log.debug("Save new Community {}", community.getId());
        return communityRepository.save(community);
    }

    private FloorPlan getOrCreateFloorPlan(AptDTO aptDTO) {
        Optional<FloorPlan> floorPlanOpt = floorPlanRepository.findById(aptDTO.getFloorplanUniqueID());
        if (floorPlanOpt.isPresent()) {
            log.debug("FloorPlan is present {}", floorPlanOpt.get().getFloorPlanId());
            return floorPlanOpt.get();
        }

        FloorPlan floorPlan = new FloorPlan();

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

        log.debug("Save new FloorPlan {}", floorPlan.getFloorPlanId());
        return floorPlanRepository.save(floorPlan);
    }

    private Unit getOrCreateUnit(AptDTO aptDTO, Community community, FloorPlan floorPlan) {
        Optional<Unit> unitOpt = unitRepository.findById(aptDTO.getObjectID());
        if (unitOpt.isPresent()) {
            log.debug("Unit is present {}", unitOpt.get().getObjectId());
            return unitOpt.get();
        }

        Unit unit = new Unit();

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

        return unitRepository.save(unit);
    }

    private void processUnitAmenities(Unit unit, List<String> amenityNames) {
        if (amenityNames == null || amenityNames.isEmpty()) {
            return;
        }

        for (String amenityName : amenityNames) {
            Optional<UnitAmenity> amenityOptional = unitAmenityRepository.findByAmenityName(amenityName);
            UnitAmenity amenity;
            if (amenityOptional.isPresent()) {
                log.debug("Amenity is present {}", amenityOptional.get().getId());
                amenity = amenityOptional.get();
            }
            else {
                amenity = new UnitAmenity();
                amenity.setAmenityName(amenityName);
                log.debug("Saving new amenity {} for unit {}", amenityName, unit.getObjectId());
                amenity = unitAmenityRepository.save(amenity);
            }
            unit.getAmenities().add(amenity);
            log.debug("Amenity {} for unit {} saved", amenity.getId(), unit.getObjectId());
        }
    }

    private void processLeasePrice(Unit unit, LeaseTermDTO earliestAvailable) {
        log.debug("Save lease price {} for {} unit", earliestAvailable.getPrice(), unit.getObjectId());

        LeasePrice earliestPrice = createLeasePrice(earliestAvailable, unit);
        unit.setUnitEarliestAvailable(earliestPrice);
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
        log.debug("Lease price created {}", leasePrice);
        return leasePrice;
    }

    private void handleRemovedUnits(Set<String> processedUnitIds, String communityId) {
        List<Unit> allUnits = unitRepository.findByCommunityId(communityId);
        List<Unit> removedUnits = allUnits.stream()
                .filter(unit -> !processedUnitIds.contains(unit.getObjectId()))
                .peek(unit -> log.debug("Unit: {}, managed: {}", unit.getObjectId(), entityManager.contains(unit)))
                .toList();

        for (Unit unit : removedUnits) {
            for (UnitAmenity amenity : unit.getAmenities()) {
                amenity.getUnits().remove(unit); // clear owning reference on the inverse side
            }
            unit.getAmenities().clear(); // clear owning side
            unit.getFloorPlan().getUnits().remove(unit);
        }

        if (!removedUnits.isEmpty()) {
            log.info("Detected {} removed units", removedUnits.size());
            log.debug("Removed units: {}", removedUnits);
            unitRepository.deleteAll(removedUnits);
            unitRepository.flush();
            entityManager.clear();
        }
    }
}
