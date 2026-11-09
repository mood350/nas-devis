package com.nasimmobilier.devis.pricing.application;

import com.nasimmobilier.devis.catalog.application.MaterialService;
import com.nasimmobilier.devis.catalog.domain.Material;
import com.nasimmobilier.devis.pricing.domain.PriceObservation;
import com.nasimmobilier.devis.pricing.domain.PriceObservation.SourceType;
import com.nasimmobilier.devis.pricing.domain.PriceObservation.Status;
import com.nasimmobilier.devis.pricing.domain.PriceObservationRepository;
import com.nasimmobilier.devis.shared.BusinessRuleException;
import com.nasimmobilier.devis.shared.NotFoundException;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import com.nasimmobilier.devis.suppliers.application.SupplierService;
import com.nasimmobilier.devis.units.application.UnitConversionService;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.criteria.Predicate;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class PriceObservationService {

    public record ObservationRequest(
            @NotNull Long materialId,
            @Schema(description = "Obligatoire pour COMMERCIAL") Long supplierId,
            @Schema(example = "Lomé") @NotBlank @Size(max = 100) String locality,
            @Schema(description = "Unite de vente observee ; defaut : unite du materiau") Long saleUnitId,
            @Schema(example = "5000") @NotNull @DecimalMin("0.00") @Digits(integer = 13, fraction = 2) BigDecimal unitPrice,
            @Schema(description = "Defaut : aujourd'hui ; jamais dans le futur") LocalDate observedOn,
            @Schema(description = "Defaut COMMERCIAL") SourceType sourceType,
            @Schema(description = "Doit correspondre aux caracteristiques du materiau", example = "CPJ 42,5 ; sac 50 kg") @Size(max = 255) String characteristics,
            @Schema(description = "Preuve : n° de devis fournisseur, facture, arrete...") @Size(max = 255) String sourceReference) {}

    public record ObservationResponse(Long id, Long materialId, Long supplierId, String locality, Long saleUnitId,
                                      BigDecimal unitPrice, LocalDate observedOn, SourceType sourceType, Status status,
                                      boolean demo, String characteristics, String sourceReference,
                                      LocalDateTime createdAt, LocalDateTime validatedAt) {}

    public record Filter(Long materialId, Long supplierId, String locality, Status status, SourceType sourceType) {}

    private final PriceObservationRepository repository;
    private final MaterialService materials;
    private final SupplierService suppliers;
    private final UnitConversionService conversions;

    public ObservationResponse create(ObservationRequest r) {
        Material m = materials.find(r.materialId());
        SourceType type = r.sourceType() != null ? r.sourceType() : SourceType.COMMERCIAL;
        if (type == SourceType.COMMERCIAL && r.supplierId() == null)
            throw new BusinessRuleException("Une observation commerciale doit identifier son fournisseur");
        if (r.supplierId() != null) suppliers.find(r.supplierId());
        LocalDate observedOn = r.observedOn() != null ? r.observedOn() : LocalDate.now();
        if (observedOn.isAfter(LocalDate.now())) throw new BusinessRuleException("Date d'observation dans le futur");
        Long saleUnit = r.saleUnitId() != null ? r.saleUnitId() : m.getUnit().getId();
        if (conversions.factor(saleUnit, m.getUnit().getId()).isEmpty())
            throw new BusinessRuleException("Aucune conversion declaree entre l'unite de vente et l'unite du materiau");
        PriceObservation o = new PriceObservation(m.getId(), r.supplierId(), r.locality().trim(), saleUnit, r.unitPrice(),
                observedOn, type, r.characteristics(), r.sourceReference());
        return toResponse(repository.saveAndFlush(o));
    }

    public ObservationResponse validate(Long id) {
        PriceObservation o = find(id);
        o.validate();
        return toResponse(o);
    }

    public ObservationResponse reject(Long id) {
        PriceObservation o = find(id);
        o.reject();
        return toResponse(o);
    }

    @Transactional(readOnly = true)
    public PageResponse<ObservationResponse> search(Filter f, PageQuery pq) {
        Specification<PriceObservation> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.materialId() != null) p.add(cb.equal(root.get("materialId"), f.materialId()));
            if (f.supplierId() != null) p.add(cb.equal(root.get("supplierId"), f.supplierId()));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            if (f.sourceType() != null) p.add(cb.equal(root.get("sourceType"), f.sourceType()));
            if (f.locality() != null && !f.locality().isBlank())
                p.add(cb.equal(cb.lower(root.get("locality")), f.locality().trim().toLowerCase()));
            if (!pq.search().isEmpty())
                p.add(cb.like(cb.lower(root.get("sourceReference")), "%" + pq.search().toLowerCase() + "%"));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pq.pageable(Set.of("id", "observedOn", "unitPrice", "createdAt"),
                Sort.by(Sort.Direction.DESC, "observedOn", "id"))), this::toResponse);
    }

    private PriceObservation find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Observation de prix", id));
    }

    private ObservationResponse toResponse(PriceObservation o) {
        return new ObservationResponse(o.getId(), o.getMaterialId(), o.getSupplierId(), o.getLocality(), o.getSaleUnitId(),
                o.getUnitPrice(), o.getObservedOn(), o.getSourceType(), o.getStatus(), o.isDemo(), o.getCharacteristics(),
                o.getSourceReference(), o.getCreatedAt(), o.getValidatedAt());
    }
}
