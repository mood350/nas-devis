package com.nasimmobilier.devis.quotes.domain;

import com.nasimmobilier.devis.pricing.domain.PricingEngine;
import com.nasimmobilier.devis.shared.BusinessRuleException;
import com.nasimmobilier.devis.shared.NotFoundException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregat devis. Lignes, taxes et identite de l'entreprise sont des SNAPSHOTS : le catalogue, les prix, les taux
 * et la configuration ne sont jamais relus apres coup. Seul un brouillon est modifiable.
 */
@Entity
@Table(name = "quotes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Quote {

    public enum Status { DRAFT, ISSUED }

    /** Taxe figee (code nul = aucune taxe). */
    public record Tax(String code, String label, BigDecimal rate) {
        public static final Tax NONE = new Tax(null, null, null);
    }

    /** Provenance figee du prix d'une ligne. */
    public record PriceInfo(String source, String confidence, String basis, boolean demo) {
        public static PriceInfo manual() {
            return new PriceInfo("MANUAL", null, "Prix saisi manuellement", false);
        }
    }

    public record LineSpec(Long materialId, String designation, String unitLabel, BigDecimal quantity,
                           BigDecimal unitPrice, Tax tax, PriceInfo price) {}

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String quoteNumber;
    @Column(nullable = false)
    private String clientName;
    private String clientAddress;
    @Column(nullable = false)
    private String projectTitle;
    /** Localisation du chantier ; sert aussi de localite pour la selection des prix. */
    @Column(nullable = false)
    private String siteLocation;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;
    @Column(nullable = false)
    private LocalDate issueDate = LocalDate.now();
    @Column(nullable = false)
    private int validityDays;
    @Column(nullable = false)
    private BigDecimal discountPercent;
    @Column(nullable = false)
    private BigDecimal transportCost;
    private String transportTaxCode;
    private String transportTaxLabel;
    private BigDecimal transportTaxRate;
    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal netHt = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal taxTotal = BigDecimal.ZERO;
    @Column(nullable = false)
    private BigDecimal totalTtc = BigDecimal.ZERO;
    @Version
    private Long version;
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Embedded
    private CompanySnapshot company;

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineOrder")
    private List<QuoteLine> lines = new ArrayList<>();

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<QuoteTaxLine> taxes = new ArrayList<>();

    @OneToMany(mappedBy = "quote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<QuoteStatusChange> history = new ArrayList<>();

    public Quote(CompanySnapshot company, String clientName, String clientAddress, String projectTitle, String siteLocation,
                 int validityDays, BigDecimal discountPercent, BigDecimal transportCost, Tax transportTax) {
        this.company = company;
        this.clientName = clientName;
        this.clientAddress = clientAddress;
        this.projectTitle = projectTitle;
        this.siteLocation = siteLocation;
        this.validityDays = validityDays;
        this.discountPercent = discountPercent;
        this.transportCost = transportCost;
        applyTransportTax(transportTax);
        history.add(new QuoteStatusChange(this, null, Status.DRAFT));
    }

    public void updateHeader(String clientName, String clientAddress, String projectTitle, String siteLocation,
                             int validityDays, BigDecimal discountPercent, BigDecimal transportCost, Tax transportTax) {
        requireDraft();
        this.clientName = clientName;
        this.clientAddress = clientAddress;
        this.projectTitle = projectTitle;
        this.siteLocation = siteLocation;
        this.validityDays = validityDays;
        this.discountPercent = discountPercent;
        this.transportCost = transportCost;
        applyTransportTax(transportTax);
        recalculate();
    }

    public void addLine(LineSpec spec) {
        requireDraft();
        lines.add(new QuoteLine(this, lines.size() + 1, spec));
        recalculate();
    }

    public void removeLine(Long lineId) {
        requireDraft();
        if (!lines.removeIf(l -> lineId.equals(l.getId()))) throw new NotFoundException("Ligne de devis", lineId);
        for (int i = 0; i < lines.size(); i++) lines.get(i).setLineOrder(i + 1);
        recalculate();
    }

    public void changeQuantity(Long lineId, BigDecimal quantity) {
        requireDraft();
        line(lineId).changeQuantity(quantity);
        recalculate();
    }

    public void repriceLine(Long lineId, BigDecimal unitPrice, PriceInfo price) {
        requireDraft();
        line(lineId).changePrice(unitPrice, price);
        recalculate();
    }

    public void issue() {
        requireDraft();
        if (lines.isEmpty()) throw new BusinessRuleException("Un devis vide ne peut pas etre emis");
        if (lines.stream().anyMatch(QuoteLine::isDemoPrice))
            throw new BusinessRuleException("Un devis contenant des prix de demonstration ne peut pas etre emis");
        status = Status.ISSUED;
        issueDate = LocalDate.now();
        history.add(new QuoteStatusChange(this, Status.DRAFT, Status.ISSUED));
    }

    public void assignNumber() {
        quoteNumber = "DEV-%d-%06d".formatted(issueDate.getYear(), id);
    }

    public LocalDate validUntil() {
        return issueDate.plusDays(validityDays);
    }

    public boolean containsDemoPrices() {
        return lines.stream().anyMatch(QuoteLine::isDemoPrice);
    }

    private QuoteLine line(Long lineId) {
        return lines.stream().filter(l -> lineId.equals(l.getId())).findFirst()
                .orElseThrow(() -> new NotFoundException("Ligne de devis", lineId));
    }

    private void applyTransportTax(Tax t) {
        Tax tax = t == null ? Tax.NONE : t;
        transportTaxCode = tax.code();
        transportTaxLabel = tax.label();
        transportTaxRate = tax.rate();
    }

    private void recalculate() {
        PricingEngine.Totals t = PricingEngine.totals(
                lines.stream().map(l -> new PricingEngine.Taxed(l.getLineTotal(), l.getTaxCode(), l.getTaxLabel(), l.getTaxRate())).toList(),
                discountPercent, transportCost, new PricingEngine.Taxed(transportCost, transportTaxCode, transportTaxLabel, transportTaxRate));
        subtotal = t.subtotal();
        discountAmount = t.discount();
        netHt = t.netHt();
        taxTotal = t.taxTotal();
        totalTtc = t.totalTtc();
        taxes.clear();
        t.taxes().forEach(x -> taxes.add(new QuoteTaxLine(this, x.code(), x.label(), x.rate(), x.base(), x.amount())));
    }

    private void requireDraft() {
        if (status != Status.DRAFT) throw new BusinessRuleException("Un devis emis est immuable");
    }
}
