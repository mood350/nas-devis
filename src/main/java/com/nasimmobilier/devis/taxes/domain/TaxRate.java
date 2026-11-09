package com.nasimmobilier.devis.taxes.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Taux configurable. Inutilisable dans un devis tant qu'il n'a pas ete valide. */
@Entity
@Table(name = "tax_rates")
@Getter @Setter @NoArgsConstructor
public class TaxRate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 30)
    private String code;
    @Column(nullable = false, length = 100)
    private String label;
    @Column(name = "rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal ratePercent;
    @Column(nullable = false)
    private boolean validated;
    private LocalDateTime validatedAt;
}
