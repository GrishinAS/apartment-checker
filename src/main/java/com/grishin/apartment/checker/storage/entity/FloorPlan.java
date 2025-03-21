package com.grishin.apartment.checker.storage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "floor_plans",
        uniqueConstraints = @UniqueConstraint(columnNames = {"propertyId", "floorPlanId"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FloorPlan {
    @Id
    private String floorPlanUniqueId;

    private String floorPlanId;
    private Integer propertyId;
    private String floorPlanName;
    private String floorPlanCrmId;
    private String floorPlanPath;
    private Integer floorPlanSqft;
    private Integer floorPlanBed;
    private Integer floorPlanBath;
    private Integer floorPlanDeposit;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @OneToMany(mappedBy = "floorPlan", cascade = CascadeType.ALL)
    private Set<Unit> units = new HashSet<>();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(mappedBy = "floorPlans")
    private Set<FloorPlanGroup> groups = new HashSet<>();
}