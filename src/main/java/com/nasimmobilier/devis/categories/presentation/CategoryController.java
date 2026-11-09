package com.nasimmobilier.devis.categories.presentation;

import com.nasimmobilier.devis.categories.application.CategoryService;
import com.nasimmobilier.devis.categories.application.CategoryService.CategoryRequest;
import com.nasimmobilier.devis.categories.application.CategoryService.CategoryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
@Tag(name = "Categories", description = "Familles de materiaux")
public class CategoryController {

    private final CategoryService service;

    @GetMapping
    @Operation(summary = "Lister les categories")
    public PageResponse<CategoryResponse> list(@ParameterObject PageQuery pq) {
        return service.list(pq);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer une categorie")
    @ApiResponse(responseCode = "201", description = "Creee")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "409", description = "Nom deja utilise")
    public CategoryResponse create(@Valid @RequestBody CategoryRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une categorie")
    @ApiResponse(responseCode = "404", description = "Categorie introuvable")
    public CategoryResponse update(@PathVariable Long id, @Valid @RequestBody CategoryRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une categorie")
    @ApiResponse(responseCode = "409", description = "Categorie utilisee par des materiaux")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
