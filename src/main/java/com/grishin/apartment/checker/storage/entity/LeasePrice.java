package com.grishin.apartment.checker.storage.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;
import java.util.Date;

@Entity
@Table(name = "lease_prices")
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
    private Boolean isEarliestAvailable;
    private Boolean isStartingPrice;

    @ManyToOne
    @JoinColumn(name = "unit_id")
    private Unit unit;
}
