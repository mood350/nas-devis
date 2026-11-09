package com.nasimmobilier.devis.units.presentation;

import com.nasimmobilier.devis.units.application.UnitService;
import com.nasimmobilier.devis.units.application.UnitService.UnitRequest;
import com.nasimmobilier.devis.units.application.UnitService.UnitResponse;
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
@RequestMapping("/api/units")
@RequiredArgsConstructor
@Tag(name = "Unites", description = "Unites de mesure (m, m2, m3, kg, sac...)")
public class UnitController {

    private final UnitService service;

    @GetMapping
    @Operation(summary = "Lister les unites")
    public PageResponse<UnitResponse> list(@ParameterObject PageQuery pq) {
        return service.list(pq);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer une unite")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "409", description = "Code deja utilise")
    public UnitResponse create(@Valid @RequestBody UnitRequest r) {
        return service.create(r);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une unite")
    @ApiResponse(responseCode = "404", description = "Unite introuvable")
    public UnitResponse update(@PathVariable Long id, @Valid @RequestBody UnitRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer une unite")
    @ApiResponse(responseCode = "409", description = "Unite utilisee par des materiaux")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
