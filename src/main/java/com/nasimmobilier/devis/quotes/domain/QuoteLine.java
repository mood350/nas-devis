package com.nasimmobilier.devis.quotes.domain;

import com.nasimmobilier.devis.pricing.domain.PricingEngine;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "quote_lines")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quote quote;
    @Setter(AccessLevel.PACKAGE)
    @Column(nullable = false)
    private int lineOrder;
    /** Reference informative ; aucune donnee du catalogue n'est relue apres creation. */
    private Long materialId;
    @Column(nullable = false)
    private String designation;
    @Column(name = "unit_label", nullable = false)
    private String unitLabel;
    @Column(nullable = false, precision = 15, scale = 3)
    private BigDecimal quantity;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal lineTotal;
    private String taxCode;
    private String taxLabel;
    private BigDecimal taxRate;
    @Column(nullable = false)
    private String priceSource;
    private String priceConfidence;
    private String priceBasis;
    @Column(nullable = false)
    private boolean demoPrice;

    QuoteLine(Quote quote, int lineOrder, Quote.LineSpec s) {
        this.quote = quote;
        this.lineOrder = lineOrder;
        this.materialId = s.materialId();
        this.designation = s.designation();
        this.unitLabel = s.unitLabel();
        this.quantity = s.quantity();
        this.taxCode = s.tax().code();
        this.taxLabel = s.tax().label();
        this.taxRate = s.tax().rate();
        changePrice(s.unitPrice(), s.price());
    }

    void changeQuantity(BigDecimal quantity) {
        this.quantity = quantity;
        this.lineTotal = PricingEngine.lineTotal(quantity, unitPrice);
    }

    void changePrice(BigDecimal unitPrice, Quote.PriceInfo info) {
        this.unitPrice = unitPrice;
        this.priceSource = info.source();
        this.priceConfidence = info.confidence();
        this.priceBasis = info.basis();
        this.demoPrice = info.demo();
        this.lineTotal = PricingEngine.lineTotal(quantity, unitPrice);
    }
}
