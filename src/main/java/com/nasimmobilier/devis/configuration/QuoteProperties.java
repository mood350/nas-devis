package com.nasimmobilier.devis.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.math.BigDecimal;

/** Regles de devis (app.quote.*). */
@ConfigurationProperties(prefix = "app.quote")
public record QuoteProperties(@DefaultValue("100") BigDecimal maxDiscountPercent) {
}
