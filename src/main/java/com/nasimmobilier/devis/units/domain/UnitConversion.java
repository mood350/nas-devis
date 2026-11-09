package com.nasimmobilier.devis.units.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/** 1 unite "from" = factor unites "to". */
@Entity
@Table(name = "unit_conversions")
@Getter @Setter @NoArgsConstructor
public class UnitConversion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "from_unit_id", nullable = false)
    private Long fromUnitId;
    @Column(name = "to_unit_id", nullable = false)
    private Long toUnitId;
    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal factor;
}
