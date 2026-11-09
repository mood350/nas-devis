package com.nasimmobilier.devis.units.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MeasureUnitRepository extends JpaRepository<MeasureUnit, Long> {
    Page<MeasureUnit> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(String code, String name, Pageable pageable);
}
