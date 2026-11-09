package com.nasimmobilier.devis.pricing.presentation;

import com.nasimmobilier.devis.pricing.application.PriceObservationService;
import com.nasimmobilier.devis.pricing.application.PriceObservationService.Filter;
import com.nasimmobilier.devis.pricing.application.PriceObservationService.ObservationRequest;
import com.nasimmobilier.devis.pricing.application.PriceObservationService.ObservationResponse;
import com.nasimmobilier.devis.pricing.application.PriceResolutionService;
import com.nasimmobilier.devis.pricing.domain.PriceObservation.SourceType;
import com.nasimmobilier.devis.pricing.domain.PriceObservation.Status;
import com.nasimmobilier.devis.pricing.domain.PriceResolution;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Prix", description = "Observations de prix (append-only), validation et resolution par mediane")
public class PriceController {

    private final PriceObservationService observations;
    private final PriceResolutionService resolution;

    @PostMapping("/price-observations")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistrer une observation (statut PENDING : ignoree tant qu'elle n'est pas validee)")
    @ApiResponse(responseCode = "404", description = "Materiau, fournisseur ou unite introuvable")
    @ApiResponse(responseCode = "422", description = "Fournisseur manquant, date future ou unite non convertible")
    public ObservationResponse create(@Valid @RequestBody ObservationRequest r) {
        return observations.create(r);
    }

    @GetMapping("/price-observations")
    @Operation(summary = "Rechercher les observations (q : reference de la source ; tri : id, observedOn, unitPrice, createdAt)")
    public PageResponse<ObservationResponse> search(@RequestParam(required = false) Long materialId,
                                                    @RequestParam(required = false) Long supplierId,
                                                    @RequestParam(required = false) String locality,
                                                    @RequestParam(required = false) Status status,
                                                    @RequestParam(required = false) SourceType sourceType,
                                                    @ParameterObject PageQuery pq) {
        return observations.search(new Filter(materialId, supplierId, locality, status, sourceType), pq);
    }

    @PostMapping("/price-observations/{id}/validate")
    @Operation(summary = "Valider une observation")
    @ApiResponse(responseCode = "422", description = "Deja traitee")
    public ObservationResponse validate(@PathVariable Long id) {
        return observations.validate(id);
    }

    @PostMapping("/price-observations/{id}/reject")
    @Operation(summary = "Rejeter une observation")
    public ObservationResponse reject(@PathVariable Long id) {
        return observations.reject(id);
    }

    @GetMapping("/materials/{materialId}/prices")
    @Operation(summary = "Historique des observations d'un materiau (toutes, du plus recent au plus ancien)")
    public PageResponse<ObservationResponse> history(@PathVariable Long materialId, @ParameterObject PageQuery pq) {
        return observations.search(new Filter(materialId, null, null, null, null), pq);
    }

    @GetMapping("/materials/{materialId}/price-resolution")
    @Operation(summary = "Prix retenu (mediane) avec confiance et provenance",
            description = "Retourne status PRICE_UNAVAILABLE (HTTP 200) si les donnees fiables sont insuffisantes. Aucun prix n'est invente.")
    public PriceResolution resolve(@PathVariable Long materialId,
                                   @Parameter(description = "Localite du chantier", example = "Lomé") @RequestParam String locality,
                                   @Parameter(description = "Date de reference, defaut aujourd'hui") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                   @Parameter(description = "Defaut COMMERCIAL ; jamais melange") @RequestParam(defaultValue = "COMMERCIAL") SourceType sourceType) {
        return resolution.resolve(materialId, locality, date != null ? date : LocalDate.now(), sourceType);
    }
}
