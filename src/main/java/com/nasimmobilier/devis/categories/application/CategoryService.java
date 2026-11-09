package com.nasimmobilier.devis.categories.application;

import com.nasimmobilier.devis.categories.domain.Category;
import com.nasimmobilier.devis.categories.domain.CategoryRepository;
import com.nasimmobilier.devis.shared.NotFoundException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import org.springframework.data.domain.Sort;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class CategoryService {

    public record CategoryRequest(@Schema(example = "Gros oeuvre") @NotBlank @Size(max = 100) String name) {}

    public record CategoryResponse(Long id, String name) {}

    private final CategoryRepository repository;

    public CategoryResponse create(CategoryRequest r) {
        Category c = new Category();
        c.setName(r.name());
        return toResponse(repository.saveAndFlush(c));
    }

    public CategoryResponse update(Long id, CategoryRequest r) {
        Category c = find(id);
        c.setName(r.name());
        repository.flush();
        return toResponse(c);
    }

    public void delete(Long id) {
        repository.delete(find(id));
        repository.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoryResponse> list(PageQuery pq) {
        return PageResponse.of(repository.findByNameContainingIgnoreCase(pq.search(),
                pq.pageable(Set.of("id", "name"), Sort.by("name"))), this::toResponse);
    }

    public Category find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Categorie", id));
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName());
    }
}
