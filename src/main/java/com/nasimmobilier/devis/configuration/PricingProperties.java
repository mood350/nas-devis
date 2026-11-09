package com.nasimmobilier.devis.configuration;

import com.nasimmobilier.devis.pricing.domain.PriceSelector;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/** Politique de selection des prix (app.pricing.*). Voir README pour la signification de chaque reglage. */
@ConfigurationProperties(prefix = "app.pricing")
public record PricingProperties(
        @DefaultValue("90") int maxObservationAgeDays,
        @DefaultValue("2") int minObservations,
        @DefaultValue("true") boolean requireSameLocality,
        @DefaultValue("false") boolean includeDemo,
        @DefaultValue("3") int highConfidenceMinObservations,
        @DefaultValue("25") BigDecimal highConfidenceMaxSpreadPercent) {

    public PriceSelector.Policy policy() {
        return new PriceSelector.Policy(maxObservationAgeDays, minObservations, requireSameLocality, includeDemo,
                highConfidenceMinObservations, highConfidenceMaxSpreadPercent);
    }
}
