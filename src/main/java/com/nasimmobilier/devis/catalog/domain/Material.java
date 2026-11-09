package com.nasimmobilier.devis.catalog.domain;

import com.nasimmobilier.devis.categories.domain.Category;
import com.nasimmobilier.devis.units.domain.MeasureUnit;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "materials")
@Getter @Setter @NoArgsConstructor
public class Material {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 50)
    private String reference;
    @Column(nullable = false, length = 200)
    private String name;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Category category;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "unit_id")
    private MeasureUnit unit;
    /** Caracteristiques commerciales (norme, diametre, dimensions...) : comparees aux observations de prix. */
    private String characteristics;
    @Column(nullable = false)
    private boolean active = true;
}
