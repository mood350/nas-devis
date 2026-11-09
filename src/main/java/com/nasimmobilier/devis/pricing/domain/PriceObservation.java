package com.nasimmobilier.devis.pricing.domain;

import com.nasimmobilier.devis.shared.BusinessRuleException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Observation de prix. Append-only : seul le statut de validation evolue (PENDING -> VALIDATED | REJECTED). */
@Entity
@Table(name = "price_observations")
@Getter
@NoArgsConstructor
public class PriceObservation {

    public enum Status { PENDING, VALIDATED, REJECTED }

    /** COMMERCIAL (marche) et ADMINISTRATIVE_REFERENCE (officiel) ne sont jamais melanges. */
    public enum SourceType { COMMERCIAL, ADMINISTRATIVE_REFERENCE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "material_id", nullable = false)
    private Long materialId;
    @Column(name = "supplier_id")
    private Long supplierId;
    @Column(nullable = false, length = 100)
    private String locality;
    @Column(name = "sale_unit_id", nullable = false)
    private Long saleUnitId;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private LocalDate observedOn;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.PENDING;
    @Column(nullable = false)
    private boolean demo;
    private String characteristics;
    private String sourceReference;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime validatedAt;

    public PriceObservation(Long materialId, Long supplierId, String locality, Long saleUnitId, BigDecimal unitPrice,
                            LocalDate observedOn, SourceType sourceType, String characteristics, String sourceReference) {
        this.materialId = materialId;
        this.supplierId = supplierId;
        this.locality = locality;
        this.saleUnitId = saleUnitId;
        this.unitPrice = unitPrice;
        this.observedOn = observedOn;
        this.sourceType = sourceType;
        this.characteristics = characteristics;
        this.sourceReference = sourceReference;
    }

    public void validate() {
        decide(Status.VALIDATED);
    }

    public void reject() {
        decide(Status.REJECTED);
    }

    private void decide(Status target) {
        if (status != Status.PENDING) throw new BusinessRuleException("Observation deja traitee : " + status);
        status = target;
        validatedAt = LocalDateTime.now();
    }
}
