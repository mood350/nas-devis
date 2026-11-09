package com.nasimmobilier.devis.catalog.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;

public interface MaterialRepository extends JpaRepository<Material, Long>, JpaSpecificationExecutor<Material> {

    /** category et unit sont charges par jointure : pas de N+1 a l'affichage de la page. */
    @Override
    @EntityGraph(attributePaths = {"category", "unit"})
    Page<Material> findAll(Specification<Material> spec, Pageable pageable);
}
