package com.grishin.apartment.checker.storage.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "units")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Unit {
    @Id
    private String unitId;

    private String unitMarketingName;
    private String unitCrmId;
    private Integer unitFloor;
    private Integer unitSqft;
    private String unitTypeCode;
    private String unitTypeName;
    private String buildingNumber;
    private Boolean unitIsStudio;
    private Boolean unitHasDiscount;
    private String featuredAmenity;
    private String objectId;

    @ManyToOne
    @JoinColumn(name = "floor_plan_unique_id")
    private FloorPlan floorPlan;

    @ManyToOne
    @JoinColumn(name = "community_id")
    private Community community;

    @ManyToMany(mappedBy = "units")
    private Set<FloorPlanGroup> groups = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "unit_amenity_mappings",
            joinColumns = @JoinColumn(name = "unit_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    private Set<UnitAmenity> amenities = new HashSet<>();

    @OneToMany(mappedBy = "unit", cascade = CascadeType.ALL)
    private Set<LeasePrice> leasePrices = new HashSet<>();
}
