package com.grishin.apartment.checker.storage.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.UniqueConstraint;
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

    @OneToMany(mappedBy = "floorPlan", cascade = CascadeType.ALL)
    private Set<Unit> units = new HashSet<>();

    @ManyToMany(mappedBy = "floorPlans")
    private Set<FloorPlanGroup> groups = new HashSet<>();
}