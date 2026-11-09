package com.nasimmobilier.devis.units.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UnitConversionRepository extends JpaRepository<UnitConversion, Long> {
    Optional<UnitConversion> findByFromUnitIdAndToUnitId(Long fromUnitId, Long toUnitId);
}
