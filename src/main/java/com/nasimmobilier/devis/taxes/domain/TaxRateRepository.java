package com.nasimmobilier.devis.taxes.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {
    Optional<TaxRate> findByCode(String code);

    Page<TaxRate> findByCodeContainingIgnoreCaseOrLabelContainingIgnoreCase(String code, String label, Pageable pageable);
}
