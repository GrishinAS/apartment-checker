package com.grishin.apartment.checker.storage.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.OneToMany;
import jakarta.persistence.CascadeType;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "communities")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Community {
    @Id
    private String id;
    private String marketingName;
    private Integer propertyId;
    private String propertyAddress;
    private String propertyZip;

    @OneToMany(mappedBy = "community", cascade = CascadeType.ALL)
    private Set<Unit> units = new HashSet<>();
}
