package com.grishin.apartment.checker.storage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Entity
@Table(name = "lease_prices", indexes = { @Index(columnList = "object_id", unique = true) })
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeasePrice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Integer price;
    private Integer term;

    @Temporal(TemporalType.DATE)
    private Date availableDate;

    private Long dateTimestamp;

    @OneToOne
    @JoinColumn(name = "object_id")
    private Unit unit;
}
