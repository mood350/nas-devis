package com.nasimmobilier.devis.units.presentation;

import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import com.nasimmobilier.devis.units.application.UnitConversionService;
import com.nasimmobilier.devis.units.application.UnitConversionService.ConversionRequest;
import com.nasimmobilier.devis.units.application.UnitConversionService.ConversionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/unit-conversions")
@RequiredArgsConstructor
@Tag(name = "Unites", description = "Conversions entre unites de vente et unites du catalogue")
public class UnitConversionController {

    private final UnitConversionService service;

    @GetMapping
    @Operation(summary = "Lister les conversions")
    public PageResponse<ConversionResponse> list(@ParameterObject PageQuery pq) {
        return service.list(pq);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Declarer une conversion", description = "1 unite source = factor unites cible. Le sens inverse est deduit.")
    @ApiResponse(responseCode = "404", description = "Unite introuvable")
    @ApiResponse(responseCode = "409", description = "Conversion deja declaree")
    @ApiResponse(responseCode = "422", description = "Unites identiques")
    public ConversionResponse create(@Valid @RequestBody ConversionRequest r) {
        return service.create(r);
    }
}
