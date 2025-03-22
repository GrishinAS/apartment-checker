package com.grishin.apartment.checker.storage.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_filter_preferences")
@Data
public class UserFilterPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "is_studio")
    private Boolean isStudio;

    @Column(name = "min_bedrooms")
    private Integer minBedrooms;

    @Column(name = "max_bedrooms")
    private Integer maxBedrooms;

    @Column(name = "min_bathrooms")
    private Double minBathrooms;

    @Column(name = "max_bathrooms")
    private Double maxBathrooms;

    @Column(name = "min_price")
    private Double minPrice;

    @Column(name = "max_price")
    private Double maxPrice;

    @Column(name = "min_floor")
    private Integer minFloor;

    @Column(name = "max_floor")
    private Integer maxFloor;

    @Column(name = "has_stainless_appliances")
    private Boolean hasStainlessAppliances;

    @Column(name = "earliest_available_from")
    private String earliestAvailableFrom;

    @Column(name = "building_number")
    private String buildingNumber;

    @Column(name = "floorplan_name")
    private String floorplanName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
