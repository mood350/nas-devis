package com.nasimmobilier.devis.quotes.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface QuoteTemplateRepository extends JpaRepository<QuoteTemplate, Long> {

    @EntityGraph(attributePaths = "items")
    Optional<QuoteTemplate> findWithItemsById(Long id);

    Page<QuoteTemplate> findByNameContainingIgnoreCase(String name, Pageable pageable);
}
