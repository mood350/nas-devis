package com.nasimmobilier.devis.catalog.application;

import com.nasimmobilier.devis.catalog.domain.Material;
import com.nasimmobilier.devis.catalog.domain.MaterialRepository;
import com.nasimmobilier.devis.categories.application.CategoryService;
import com.nasimmobilier.devis.shared.NotFoundException;
import com.nasimmobilier.devis.units.application.UnitService;
import io.swagger.v3.oas.annotations.media.Schema;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import java.util.ArrayList;
import java.util.Set;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialService {

    public record MaterialRequest(@Schema(example = "CIM-001") @NotBlank @Size(max = 50) String reference,
                                  @Schema(example = "Ciment CPJ 35 - sac de 50 kg") @NotBlank @Size(max = 200) String name,
                                  @NotNull Long categoryId,
                                  @NotNull Long unitId,
                                  @Schema(example = "CPJ 42,5 ; sac 50 kg") @Size(max = 255) String characteristics,
                                  Boolean active) {}

    public record MaterialResponse(Long id, String reference, String name, Long categoryId, String categoryName,
                                   Long unitId, String unitCode, String characteristics, boolean active) {}

    private final MaterialRepository repository;
    private final CategoryService categories;
    private final UnitService units;

    public MaterialResponse create(MaterialRequest r) {
        Material m = new Material();
        apply(m, r);
        return toResponse(repository.saveAndFlush(m));
    }

    public MaterialResponse update(Long id, MaterialRequest r) {
        Material m = find(id);
        apply(m, r);
        repository.flush();
        return toResponse(m);
    }

    @Transactional(readOnly = true)
    public PageResponse<MaterialResponse> list(Long categoryId, Boolean active, PageQuery pq) {
        Specification<Material> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (categoryId != null) p.add(cb.equal(root.get("category").get("id"), categoryId));
            if (active != null) p.add(cb.equal(root.get("active"), active));
            if (!pq.search().isEmpty()) {
                String like = "%" + pq.search().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("name")), like), cb.like(cb.lower(root.get("reference")), like)));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return PageResponse.of(repository.findAll(spec, pq.pageable(Set.of("id", "reference", "name", "active"), Sort.by("name"))),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public MaterialResponse get(Long id) {
        return toResponse(find(id));
    }

    public Material find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Materiau", id));
    }

    private void apply(Material m, MaterialRequest r) {
        m.setReference(r.reference());
        m.setName(r.name());
        m.setCategory(categories.find(r.categoryId()));
        m.setUnit(units.find(r.unitId()));
        m.setCharacteristics(r.characteristics());
        if (r.active() != null) m.setActive(r.active());
    }

    private MaterialResponse toResponse(Material m) {
        return new MaterialResponse(m.getId(), m.getReference(), m.getName(), m.getCategory().getId(),
                m.getCategory().getName(), m.getUnit().getId(), m.getUnit().getCode(), m.getCharacteristics(), m.isActive());
    }
}
