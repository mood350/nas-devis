package com.nasimmobilier.devis.pricing.domain;

import com.nasimmobilier.devis.pricing.domain.PriceResolution.Confidence;
import com.nasimmobilier.devis.pricing.domain.PriceResolution.Exclusion;
import com.nasimmobilier.devis.pricing.domain.PriceResolution.Status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Selection pure (sans I/O) du prix d'un materiau : filtre les observations compatibles, prend la MEDIANE,
 * calcule un niveau de confiance et explique le resultat. Ne produit jamais de prix sans donnees fiables.
 */
public final class PriceSelector {

    /** Politique configurable (voir PricingProperties). */
    public record Policy(int maxAgeDays, int minObservations, boolean requireSameLocality, boolean includeDemo,
                         int highConfidenceMinObservations, BigDecimal highConfidenceMaxSpreadPercent) {}

    /** priceInMaterialUnit est nul si l'unite de vente n'est pas convertible vers l'unite du materiau. */
    public record Candidate(Long id, Long supplierId, String locality, String characteristics, LocalDate observedOn,
                            PriceObservation.Status status, boolean demo, BigDecimal priceInMaterialUnit) {}

    private PriceSelector() {}

    public static PriceResolution select(String materialCharacteristics, String locality, LocalDate asOf,
                                         List<Candidate> candidates, Policy policy) {
        List<Exclusion> excluded = new ArrayList<>();
        List<Candidate> kept = new ArrayList<>();
        for (Candidate c : candidates) {
            String reason = exclusionReason(c, materialCharacteristics, locality, asOf, policy);
            if (reason == null) kept.add(c); else excluded.add(new Exclusion(c.id(), reason));
        }
        if (kept.isEmpty() || kept.size() < policy.minObservations()) {
            return new PriceResolution(Status.PRICE_UNAVAILABLE, null, null, kept.size(), 0, false, List.of(), excluded,
                    "Observations valides insuffisantes : %d retenue(s), %d requise(s), %d exclue(s)."
                            .formatted(kept.size(), policy.minObservations(), excluded.size()));
        }
        BigDecimal median = median(kept.stream().map(Candidate::priceInMaterialUnit).sorted().toList());
        int suppliers = (int) kept.stream().map(Candidate::supplierId).filter(Objects::nonNull).distinct().count();
        BigDecimal min = kept.stream().map(Candidate::priceInMaterialUnit).min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = kept.stream().map(Candidate::priceInMaterialUnit).max(Comparator.naturalOrder()).orElseThrow();
        BigDecimal spread = median.signum() == 0 ? BigDecimal.ZERO
                : max.subtract(min).multiply(BigDecimal.valueOf(100)).divide(median, 1, RoundingMode.HALF_UP);
        Confidence confidence = kept.size() >= policy.highConfidenceMinObservations() && suppliers >= 2
                && spread.compareTo(policy.highConfidenceMaxSpreadPercent()) <= 0 ? Confidence.HIGH
                : kept.size() >= 2 && suppliers >= 2 ? Confidence.MEDIUM : Confidence.LOW; // un seul fournisseur = une seule source
        boolean demo = kept.stream().anyMatch(Candidate::demo);
        LocalDate oldest = kept.stream().map(Candidate::observedOn).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate newest = kept.stream().map(Candidate::observedOn).max(Comparator.naturalOrder()).orElseThrow();
        String explanation = "Mediane de %d observation(s) validee(s) du %s au %s, %d fournisseur(s), ecart min/max %s %%, confiance %s%s."
                .formatted(kept.size(), oldest, newest, suppliers, spread, confidence, demo ? " - PRIX DE DEMONSTRATION" : "");
        return new PriceResolution(Status.OK, median, confidence, kept.size(), suppliers, demo,
                kept.stream().map(Candidate::id).toList(), excluded, explanation);
    }

    private static String exclusionReason(Candidate c, String materialCharacteristics, String locality, LocalDate asOf, Policy p) {
        if (c.status() != PriceObservation.Status.VALIDATED) return "NOT_VALIDATED";
        if (c.demo() && !p.includeDemo()) return "DEMO_EXCLUDED";
        if (c.observedOn().isAfter(asOf)) return "FUTURE_DATE";
        if (p.requireSameLocality() && !norm(c.locality()).equals(norm(locality))) return "LOCALITY_MISMATCH";
        if (materialCharacteristics != null && !materialCharacteristics.isBlank()
                && !norm(materialCharacteristics).equals(norm(c.characteristics()))) return "CHARACTERISTICS_MISMATCH";
        if (c.priceInMaterialUnit() == null) return "UNIT_INCOMPATIBLE";
        if (ChronoUnit.DAYS.between(c.observedOn(), asOf) > p.maxAgeDays()) return "STALE";
        return null;
    }

    /** Mediane ; pour un nombre pair, moyenne des deux valeurs centrales (liste deja triee). */
    static BigDecimal median(List<BigDecimal> sorted) {
        int n = sorted.size();
        BigDecimal m = n % 2 == 1 ? sorted.get(n / 2)
                : sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
        return m.setScale(2, RoundingMode.HALF_UP);
    }

    private static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}
