package com.nasimmobilier.devis.quotes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Identite de l'entreprise figee a la creation du devis : les exports restent reproductibles. */
@Embeddable
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanySnapshot {
    @Column(name = "company_name", nullable = false)
    private String companyName;
    @Column(name = "company_tagline", nullable = false)
    private String companyTagline;
    @Column(name = "company_address", nullable = false)
    private String companyAddress;
    @Column(name = "company_phone", nullable = false)
    private String companyPhone;
    @Column(name = "company_email", nullable = false)
    private String companyEmail;
    @Column(name = "company_nif", nullable = false)
    private String companyNif;
}
