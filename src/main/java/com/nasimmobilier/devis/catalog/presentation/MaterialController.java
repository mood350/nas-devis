package com.nasimmobilier.devis.catalog.presentation;

import com.nasimmobilier.devis.catalog.application.MaterialService;
import com.nasimmobilier.devis.catalog.application.MaterialService.MaterialRequest;
import com.nasimmobilier.devis.catalog.application.MaterialService.MaterialResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import org.springdoc.core.annotations.ParameterObject;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
@Tag(name = "Catalogue", description = "Catalogue des materiaux")
public class MaterialController {

    private final MaterialService service;

    @GetMapping
    @Operation(summary = "Rechercher les materiaux (q : nom ou reference ; tri : id, reference, name, active)")
    public PageResponse<MaterialResponse> list(@Parameter(description = "Filtre categorie") @RequestParam(required = false) Long categoryId,
                                               @Parameter(description = "Filtre actif/inactif") @RequestParam(required = false) Boolean active,
                                               @ParameterObject PageQuery pq) {
        return service.list(categoryId, active, pq);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un materiau")
    @ApiResponse(responseCode = "404", description = "Materiau introuvable")
    public MaterialResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un materiau")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "404", description = "Categorie ou unite introuvable")
    @ApiResponse(responseCode = "409", description = "Reference deja utilisee")
    public MaterialResponse create(@Valid @RequestBody MaterialRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un materiau (les anciens devis ne changent pas : snapshots)")
    @ApiResponse(responseCode = "404", description = "Materiau introuvable")
    public MaterialResponse update(@PathVariable Long id, @Valid @RequestBody MaterialRequest r) {
        return service.update(id, r);
    }
}
