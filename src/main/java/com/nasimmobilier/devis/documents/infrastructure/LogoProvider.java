package com.nasimmobilier.devis.documents.infrastructure;

import com.nasimmobilier.devis.configuration.CompanyProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/** Logo PNG de l'entreprise ; son absence n'empeche pas l'export. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LogoProvider {

    private final CompanyProperties company;

    public Optional<byte[]> bytes() {
        try {
            if (company.logo() == null || !company.logo().exists()) return Optional.empty();
            return Optional.of(company.logo().getContentAsByteArray());
        } catch (IOException e) {
            log.warn("Logo illisible : {}", e.getMessage());
            return Optional.empty();
        }
    }
}
