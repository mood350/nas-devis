package com.nasimmobilier.devis.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.core.io.Resource;

/** Identite de l'entreprise (app.company.*) ; copiee dans chaque devis a sa creation. Le logo (PNG) reste lu a l'export. */
@ConfigurationProperties(prefix = "app.company")
public record CompanyProperties(
        @DefaultValue("NASIM IMMOBILIER") String name,
        @DefaultValue("Entreprise de construction - BTP") String tagline,
        @DefaultValue("Lome, Togo") String address,
        @DefaultValue("") String phone,
        @DefaultValue("") String email,
        @DefaultValue("") String nif,
        @DefaultValue("classpath:logo.png") Resource logo) {
}
