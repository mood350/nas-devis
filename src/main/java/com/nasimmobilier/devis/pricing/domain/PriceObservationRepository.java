package com.nasimmobilier.devis.pricing.domain;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long>, JpaSpecificationExecutor<PriceObservation> {

    /** Candidats a une resolution : un seul type de source a la fois (pas de melange). */
    List<PriceObservation> findByMaterialIdAndSourceType(Long materialId, PriceObservation.SourceType sourceType, Pageable pageable);
}
