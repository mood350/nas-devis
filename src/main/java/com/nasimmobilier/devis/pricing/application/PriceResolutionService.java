package com.nasimmobilier.devis.pricing.application;

import com.nasimmobilier.devis.catalog.application.MaterialService;
import com.nasimmobilier.devis.catalog.domain.Material;
import com.nasimmobilier.devis.configuration.PricingProperties;
import com.nasimmobilier.devis.pricing.domain.PriceObservation;
import com.nasimmobilier.devis.pricing.domain.PriceObservationRepository;
import com.nasimmobilier.devis.pricing.domain.PriceResolution;
import com.nasimmobilier.devis.pricing.domain.PriceSelector;
import com.nasimmobilier.devis.pricing.domain.PriceSelector.Candidate;
import com.nasimmobilier.devis.units.application.UnitConversionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Charge les observations, convertit les unites de vente, puis delegue a PriceSelector (logique pure). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PriceResolutionService {

    /** Fenetre de candidats examines : les plus recents d'abord (les plus anciens seraient de toute facon perimes). */
    private static final int MAX_CANDIDATES = 200;
    private static final int MAX_EXPLAINED = 20;

    private final PriceObservationRepository observations;
    private final MaterialService materials;
    private final UnitConversionService conversions;
    private final PricingProperties properties;

    public PriceResolution resolve(Long materialId, String locality, LocalDate asOf, PriceObservation.SourceType type) {
        Material m = materials.find(materialId);
        Map<Long, Optional<BigDecimal>> factors = new HashMap<>(); // une requete par unite de vente, pas par observation
        Sort recent = Sort.by(Sort.Direction.DESC, "observedOn", "id");
        List<PriceObservation> rows = new ArrayList<>(observations.findByMaterialIdAndSourceTypeAndStatus(
                materialId, type, PriceObservation.Status.VALIDATED, PageRequest.of(0, MAX_CANDIDATES, recent)));
        rows.addAll(observations.findByMaterialIdAndSourceTypeAndStatusNot(
                materialId, type, PriceObservation.Status.VALIDATED, PageRequest.of(0, MAX_EXPLAINED, recent)));
        List<Candidate> candidates = rows.stream().map(o -> toCandidate(o, m.getUnit().getId(), factors)).toList();
        return PriceSelector.select(m.getCharacteristics(), locality, asOf, candidates, properties.policy());
    }

    /** Prix par unite du materiau = prix de vente / (nombre d'unites materiau par unite de vente). */
    private Candidate toCandidate(PriceObservation o, Long materialUnitId, Map<Long, Optional<BigDecimal>> factors) {
        BigDecimal price = factors.computeIfAbsent(o.getSaleUnitId(), u -> conversions.factor(u, materialUnitId))
                .map(f -> o.getUnitPrice().divide(f, 4, RoundingMode.HALF_UP)).orElse(null);
        return new Candidate(o.getId(), o.getSupplierId(), o.getLocality(), o.getCharacteristics(), o.getObservedOn(),
                o.getStatus(), o.isDemo(), price);
    }
}
