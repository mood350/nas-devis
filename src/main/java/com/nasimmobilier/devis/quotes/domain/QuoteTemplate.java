package com.nasimmobilier.devis.quotes.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Modele predefini : liste de (materiau, quantite) a instancier en devis aux prix du jour. */
@Entity
@Table(name = "quote_templates")
@SQLRestriction("deleted_at is null")
@Getter @Setter @NoArgsConstructor
public class QuoteTemplate {

    @Entity
    @Table(name = "quote_template_items")
    @Getter @Setter @NoArgsConstructor
    public static class Item {
        @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;
        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        private QuoteTemplate template;
        @Column(name = "material_id", nullable = false)
        private Long materialId;
        @Column(nullable = false, precision = 15, scale = 3)
        private BigDecimal quantity;
    }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 150)
    private String name;
    private String description;
    private LocalDateTime deletedAt;
    @BatchSize(size = 50) // evite le N+1 sur les listes paginees
    @OneToMany(mappedBy = "template", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Item> items = new ArrayList<>();

    /** Suppression douce ; le nom est libere (contrainte d'unicite) en le suffixant. */
    public void softDelete() {
        String suffix = " [supprime #" + id + "]";
        name = name.substring(0, Math.min(name.length(), 150 - suffix.length())) + suffix;
        deletedAt = LocalDateTime.now();
    }

    public void addItem(Long materialId, BigDecimal quantity) {
        Item i = new Item();
        i.setTemplate(this);
        i.setMaterialId(materialId);
        i.setQuantity(quantity);
        items.add(i);
    }
}
