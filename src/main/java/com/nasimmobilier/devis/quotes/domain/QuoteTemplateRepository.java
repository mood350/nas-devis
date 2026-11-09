package com.nasimmobilier.devis.quotes.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuoteTemplateRepository extends JpaRepository<QuoteTemplate, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<QuoteTemplate> findWithItemsById(Long id);

    @EntityGraph(attributePaths = "items")
    List<QuoteTemplate> findAllByOrderByName();
}
