package com.nasimmobilier.devis.quotes.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {

    /** Lignes chargees en une requete ; taxes et historique le sont paresseusement (une requete chacun, pas de N+1). */
    @EntityGraph(attributePaths = "lines")
    Optional<Quote> findWithLinesById(Long id);
}
