package com.nasimmobilier.devis.taxes.presentation;

import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import com.nasimmobilier.devis.taxes.application.TaxRateService;
import com.nasimmobilier.devis.taxes.application.TaxRateService.TaxRateRequest;
import com.nasimmobilier.devis.taxes.application.TaxRateService.TaxRateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tax-rates")
@RequiredArgsConstructor
@Tag(name = "Taxes", description = "Taux fiscaux configurables. Aucun taux n'existe ni ne s'applique par defaut.")
public class TaxRateController {

    private final TaxRateService service;

    @GetMapping
    @Operation(summary = "Lister les taux")
    public PageResponse<TaxRateResponse> list(@ParameterObject PageQuery pq) {
        return service.list(pq);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Declarer un taux (non valide, donc inutilisable)")
    @ApiResponse(responseCode = "409", description = "Code deja utilise")
    public TaxRateResponse create(@Valid @RequestBody TaxRateRequest r) {
        return service.create(r);
    }

    @PostMapping("/{code}/validate")
    @Operation(summary = "Valider un taux pour usage dans les devis")
    @ApiResponse(responseCode = "404", description = "Taux introuvable")
    public TaxRateResponse validate(@PathVariable String code) {
        return service.validate(code);
    }
}
