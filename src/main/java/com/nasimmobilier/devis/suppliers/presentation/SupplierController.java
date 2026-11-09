package com.nasimmobilier.devis.suppliers.presentation;

import com.nasimmobilier.devis.suppliers.application.SupplierService;
import com.nasimmobilier.devis.suppliers.application.SupplierService.SupplierRequest;
import com.nasimmobilier.devis.suppliers.application.SupplierService.SupplierResponse;
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
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@Tag(name = "Fournisseurs")
public class SupplierController {

    private final SupplierService service;

    @GetMapping
    @Operation(summary = "Lister les fournisseurs")
    public PageResponse<SupplierResponse> list(@ParameterObject PageQuery pq) {
        return service.list(pq);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un fournisseur")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "409", description = "Nom deja utilise")
    public SupplierResponse create(@Valid @RequestBody SupplierRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un fournisseur")
    @ApiResponse(responseCode = "404", description = "Fournisseur introuvable")
    public SupplierResponse update(@PathVariable Long id, @Valid @RequestBody SupplierRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un fournisseur")
    @ApiResponse(responseCode = "409", description = "Fournisseur reference par des prix")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
