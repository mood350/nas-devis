package com.nasimmobilier.devis.pricing.domain;

import java.math.BigDecimal;
import java.util.List;

/** Resultat explique d'une selection de prix. Si status = PRICE_UNAVAILABLE, price et confidence sont nuls. */
public record PriceResolution(Status status, BigDecimal price, Confidence confidence, int observationsUsed,
                              int distinctSuppliers, boolean demo, List<Long> usedObservationIds,
                              List<Exclusion> excluded, String explanation) {

    public enum Status { OK, PRICE_UNAVAILABLE }

    public enum Confidence { HIGH, MEDIUM, LOW }

    public record Exclusion(Long observationId, String reason) {}
}
