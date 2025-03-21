package com.grishin.apartment.checker.storage.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Column;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "unit_amenities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UnitAmenity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String amenityName;

    @ManyToMany(mappedBy = "amenities")
    private Set<Unit> units = new HashSet<>();
}
