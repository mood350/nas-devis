package com.nasimmobilier.devis.quotes.application;

import com.nasimmobilier.devis.catalog.application.MaterialService;
import com.nasimmobilier.devis.catalog.domain.Material;
import com.nasimmobilier.devis.configuration.CompanyProperties;
import com.nasimmobilier.devis.configuration.QuoteProperties;
import com.nasimmobilier.devis.pricing.application.PriceResolutionService;
import com.nasimmobilier.devis.pricing.domain.PriceObservation.SourceType;
import com.nasimmobilier.devis.pricing.domain.PriceResolution;
import com.nasimmobilier.devis.quotes.domain.*;
import com.nasimmobilier.devis.shared.*;
import com.nasimmobilier.devis.taxes.application.TaxRateService;
import com.nasimmobilier.devis.taxes.domain.TaxRate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Cas d'usage devis. Chaque methode publique est UNE transaction : tout ou rien. */
@Service
@RequiredArgsConstructor
@Transactional
public class QuoteService {

    public record LineRequest(
            @Schema(description = "Materiau du catalogue : designation, unite et prix sont figes depuis le catalogue", example = "1") Long materialId,
            @Schema(description = "Obligatoire sans materialId", example = "Main d'oeuvre macon") @Size(max = 255) String designation,
            @Schema(description = "Obligatoire sans materialId", example = "f") @Size(max = 20) String unit,
            @Schema(example = "12.5") @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity,
            @Schema(description = "Prix manuel (sinon prix resolu depuis les observations validees)", example = "6500") @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
            @Schema(description = "Code d'un taux valide ; defaut : taxCode du devis ; sinon aucune taxe") @Size(max = 30) String taxCode) {}

    public record QuoteRequest(
            @Schema(example = "M. Koffi Agbodjan") @NotBlank @Size(max = 200) String clientName,
            @Schema(example = "Quartier Adidogome, Lomé") @Size(max = 255) String clientAddress,
            @Schema(example = "Construction d'un mur de clôture") @NotBlank @Size(max = 255) String projectTitle,
            @Schema(description = "Localisation du chantier = localite de reference des prix", example = "Lomé") @NotBlank @Size(max = 255) String siteLocation,
            @Schema(description = "Defaut 30", example = "30") @Min(1) @Max(365) Integer validityDays,
            @Schema(description = "Remise en %, appliquee avant taxe ; defaut 0", example = "5") @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
            @Schema(description = "Frais de transport HT, jamais remises ; defaut 0", example = "25000") @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal transportCost,
            @Schema(description = "Taxe sur le transport (code valide) ; defaut aucune") @Size(max = 30) String transportTaxCode,
            @Schema(description = "Taxe par defaut des lignes sans taxCode ; defaut aucune") @Size(max = 30) String taxCode,
            @Schema(description = "Obligatoire a la creation ; ignore en modification (utiliser les endpoints de lignes)") @Valid List<LineRequest> lines) {}

    public record QuantityRequest(@NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity) {}

    public record LineResponse(Long id, int order, Long materialId, String designation, String unit, BigDecimal quantity,
                               BigDecimal unitPrice, BigDecimal lineTotal, String taxCode, BigDecimal taxRate,
                               String priceSource, String priceConfidence, String priceBasis, boolean demoPrice) {}

    public record TaxResponse(String code, String label, BigDecimal rate, BigDecimal base, BigDecimal amount) {}

    public record StatusChangeResponse(String from, String to, LocalDateTime at) {}

    public record CompanyInfo(String name, String tagline, String address, String phone, String email, String nif) {}

    public record QuoteResponse(Long id, Long version, String number, String status, CompanyInfo company, String clientName,
                                String clientAddress, String projectTitle, String siteLocation, LocalDate issueDate,
                                LocalDate validUntil, int validityDays, BigDecimal discountPercent,
                                BigDecimal subtotal, BigDecimal discountAmount, BigDecimal transportCost, BigDecimal netHt,
                                List<TaxResponse> taxes, BigDecimal taxTotal, BigDecimal totalTtc,
                                boolean containsDemoPrices, List<LineResponse> lines, List<StatusChangeResponse> history) {}

    public record QuoteSummary(Long id, String number, String status, String clientName, String projectTitle,
                               BigDecimal totalTtc, LocalDate issueDate) {}

    private final QuoteRepository repository;
    private final MaterialService materials;
    private final PriceResolutionService resolution;
    private final TaxRateService taxRates;
    private final CompanyProperties company;
    private final QuoteProperties quoteProperties;

    public QuoteResponse create(QuoteRequest r) {
        if (r.lines() == null || r.lines().isEmpty()) throw new BusinessRuleException("Un devis doit comporter au moins une ligne");
        BigDecimal discount = discount(r);
        Quote q = new Quote(new CompanySnapshot(company.name(), company.tagline(), company.address(), company.phone(),
                company.email(), company.nif()), r.clientName(), r.clientAddress(), r.projectTitle(), r.siteLocation(),
                validity(r), discount, transport(r), tax(r.transportTaxCode()));
        r.lines().forEach(l -> q.addLine(spec(q.getSiteLocation(), l, r.taxCode())));
        repository.saveAndFlush(q); // l'id identity sert au numero ; unicite garantie par la contrainte SQL
        q.assignNumber();
        return toResponse(q);
    }

    @Transactional(readOnly = true)
    public QuoteResponse get(Long id) {
        return toResponse(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<QuoteSummary> search(Quote.Status status, PageQuery pq) {
        Specification<Quote> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (status != null) p.add(cb.equal(root.get("status"), status));
            if (!pq.search().isEmpty()) {
                String like = "%" + pq.search().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("clientName")), like), cb.like(cb.lower(root.get("projectTitle")), like),
                        cb.like(cb.lower(root.get("quoteNumber")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pq.pageable(Set.of("id", "issueDate", "clientName", "totalTtc", "status"),
                Sort.by(Sort.Direction.DESC, "id"))), q -> new QuoteSummary(q.getId(), q.getQuoteNumber(), q.getStatus().name(),
                q.getClientName(), q.getProjectTitle(), q.getTotalTtc(), q.getIssueDate()));
    }

    /** Modifie l'en-tete d'un brouillon (client, chantier, validite, remise, transport) et recalcule. */
    public QuoteResponse updateHeader(Long id, QuoteRequest r, Long expectedVersion) {
        Quote q = find(id, expectedVersion);
        boolean siteChanged = !q.getSiteLocation().equals(r.siteLocation());
        q.updateHeader(r.clientName(), r.clientAddress(), r.projectTitle(), r.siteLocation(), validity(r), discount(r),
                transport(r), tax(r.transportTaxCode()));
        if (siteChanged) repriceCatalogLines(q); // les prix dependent de la localite ; PRICE_UNAVAILABLE annule tout
        return flushAndRespond(q);
    }

    public QuoteResponse addLine(Long id, LineRequest l, String defaultTaxCode, Long expectedVersion) {
        Quote q = find(id, expectedVersion);
        q.addLine(spec(q.getSiteLocation(), l, defaultTaxCode));
        return flushAndRespond(q);
    }

    public QuoteResponse changeQuantity(Long id, Long lineId, QuantityRequest r, Long expectedVersion) {
        Quote q = find(id, expectedVersion);
        q.changeQuantity(lineId, r.quantity());
        return flushAndRespond(q);
    }

    public QuoteResponse removeLine(Long id, Long lineId, Long expectedVersion) {
        Quote q = find(id, expectedVersion);
        q.removeLine(lineId);
        return flushAndRespond(q);
    }

    /** Re-selectionne les prix des lignes issues du catalogue (les prix manuels sont conserves). Tout ou rien. */
    public QuoteResponse reprice(Long id) {
        Quote q = find(id);
        repriceCatalogLines(q);
        return flushAndRespond(q);
    }

    private void repriceCatalogLines(Quote q) {
        for (QuoteLine l : List.copyOf(q.getLines())) {
            if (l.getMaterialId() == null || !"CATALOG".equals(l.getPriceSource())) continue;
            Resolved r = resolve(materials.find(l.getMaterialId()), q.getSiteLocation());
            q.repriceLine(l.getId(), r.price(), r.info());
        }
    }

    /** expectedVersion (optionnelle, sur toutes les modifications) : celle que l'utilisateur a vue ; sinon HTTP 409. */
    public QuoteResponse issue(Long id, Long expectedVersion) {
        Quote q = find(id, expectedVersion);
        q.issue();
        return flushAndRespond(q);
    }

    public void delete(Long id) {
        repository.findById(id).orElseThrow(() -> new NotFoundException("Devis", id)).softDelete();
    }

    private Quote find(Long id, Long expectedVersion) {
        Quote q = find(id);
        if (expectedVersion != null && !expectedVersion.equals(q.getVersion()))
            throw new ObjectOptimisticLockingFailureException(Quote.class, id);
        return q;
    }

    private Quote find(Long id) {
        return repository.findWithLinesById(id).orElseThrow(() -> new NotFoundException("Devis", id));
    }

    /** flush : detecte un conflit de version (HTTP 409) avant de repondre. */
    private QuoteResponse flushAndRespond(Quote q) {
        repository.flush();
        return toResponse(q);
    }

    private record Resolved(BigDecimal price, Quote.PriceInfo info) {}

    private Resolved resolve(Material m, String locality) {
        PriceResolution pr = resolution.resolve(m.getId(), locality, LocalDate.now(), SourceType.COMMERCIAL);
        if (pr.status() == PriceResolution.Status.PRICE_UNAVAILABLE)
            throw new PriceUnavailableException(m.getReference(), pr.explanation());
        return new Resolved(pr.price(), new Quote.PriceInfo("CATALOG", pr.confidence().name(), pr.explanation(), pr.demo()));
    }

    private Quote.LineSpec spec(String locality, LineRequest l, String defaultTaxCode) {
        Quote.Tax tax = tax(l.taxCode() != null && !l.taxCode().isBlank() ? l.taxCode() : defaultTaxCode);
        if (l.materialId() != null) {
            Material m = materials.find(l.materialId());
            if (!m.isActive()) throw new BusinessRuleException("Materiau inactif : " + m.getReference());
            Resolved r = l.unitPrice() != null ? new Resolved(l.unitPrice(), Quote.PriceInfo.manual()) : resolve(m, locality);
            return new Quote.LineSpec(m.getId(), l.designation() != null ? l.designation() : m.getName(), m.getUnit().getCode(),
                    l.quantity(), r.price(), tax, r.info());
        }
        if (l.designation() == null || l.unit() == null || l.unitPrice() == null)
            throw new BusinessRuleException("Sans materialId, designation, unit et unitPrice sont obligatoires");
        return new Quote.LineSpec(null, l.designation(), l.unit(), l.quantity(), l.unitPrice(), tax, Quote.PriceInfo.manual());
    }

    private Quote.Tax tax(String code) {
        TaxRate t = taxRates.applicable(code);
        return t == null ? Quote.Tax.NONE : new Quote.Tax(t.getCode(), t.getLabel(), t.getRatePercent());
    }

    private static int validity(QuoteRequest r) {
        return r.validityDays() != null ? r.validityDays() : 30;
    }

    private static BigDecimal transport(QuoteRequest r) {
        return r.transportCost() != null ? r.transportCost() : BigDecimal.ZERO;
    }

    private BigDecimal discount(QuoteRequest r) {
        BigDecimal d = r.discountPercent() != null ? r.discountPercent() : BigDecimal.ZERO;
        if (d.compareTo(quoteProperties.maxDiscountPercent()) > 0)
            throw new BusinessRuleException("Remise superieure au maximum autorise (%s %%)".formatted(quoteProperties.maxDiscountPercent()));
        return d;
    }

    private QuoteResponse toResponse(Quote q) {
        CompanySnapshot c = q.getCompany();
        return new QuoteResponse(q.getId(), q.getVersion(), q.getQuoteNumber(), q.getStatus().name(),
                new CompanyInfo(c.getCompanyName(), c.getCompanyTagline(), c.getCompanyAddress(), c.getCompanyPhone(),
                        c.getCompanyEmail(), c.getCompanyNif()),
                q.getClientName(), q.getClientAddress(), q.getProjectTitle(), q.getSiteLocation(), q.getIssueDate(),
                q.validUntil(), q.getValidityDays(), q.getDiscountPercent(), q.getSubtotal(), q.getDiscountAmount(),
                q.getTransportCost(), q.getNetHt(),
                q.getTaxes().stream().map(t -> new TaxResponse(t.getCode(), t.getLabel(), t.getRate(), t.getBaseAmount(), t.getTaxAmount())).toList(),
                q.getTaxTotal(), q.getTotalTtc(), q.containsDemoPrices(),
                q.getLines().stream().map(this::toResponse).toList(),
                q.getHistory().stream().map(h -> new StatusChangeResponse(h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(), h.getChangedAt())).toList());
    }

    private LineResponse toResponse(QuoteLine l) {
        return new LineResponse(l.getId(), l.getLineOrder(), l.getMaterialId(), l.getDesignation(), l.getUnitLabel(),
                l.getQuantity(), l.getUnitPrice(), l.getLineTotal(), l.getTaxCode(), l.getTaxRate(), l.getPriceSource(),
                l.getPriceConfidence(), l.getPriceBasis(), l.isDemoPrice());
    }
}
