package com.nasimmobilier.devis.quotes.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Ventilation fiscale figee d'un devis (une ligne par taux applique). */
@Entity
@Table(name = "quote_taxes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuoteTaxLine {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Quote quote;
    private String code;
    private String label;
    private BigDecimal rate;
    private BigDecimal baseAmount;
    private BigDecimal taxAmount;

    QuoteTaxLine(Quote quote, String code, String label, BigDecimal rate, BigDecimal baseAmount, BigDecimal taxAmount) {
        this.quote = quote;
        this.code = code;
        this.label = label;
        this.rate = rate;
        this.baseAmount = baseAmount;
        this.taxAmount = taxAmount;
    }
}
