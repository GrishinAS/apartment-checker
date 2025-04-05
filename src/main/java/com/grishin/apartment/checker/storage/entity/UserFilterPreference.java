package com.grishin.apartment.checker.storage.entity;

import jakarta.persistence.*;
import lombok.*;

import java.sql.Date;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "user_filter_preferences")
@Data
@Builder
public class UserFilterPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "selected_community")
    private String selectedCommunity;

    @Column(name = "is_studio")
    private Boolean isStudio;

    @Column(name = "min_bedrooms")
    private Integer minBedrooms;

    @Column(name = "max_bedrooms")
    private Integer maxBedrooms;

    @Column(name = "min_bathrooms")
    private Integer minBathrooms;

    @Column(name = "max_bathrooms")
    private Integer maxBathrooms;

    @Column(name = "min_price")
    private Integer minPrice;

    @Column(name = "max_price")
    private Integer maxPrice;

    @Column(name = "min_floor")
    private Integer minFloor;

    @Column(name = "max_floor")
    private Integer maxFloor;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "preference_amenity_mappings",
            joinColumns = @JoinColumn(name = "id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    private Set<UnitAmenity> amenities = new HashSet<>();

    @Column(name = "available_from")
    private Date availableFrom;

    @Column(name = "available_until")
    private Date availableUntil;

    @Column(name = "building_number")
    private String buildingNumber;

    @Column(name = "floorplan_name")
    private String floorplanName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
