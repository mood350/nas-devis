package com.nasimmobilier.devis.quotes.presentation;

import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteResponse;
import com.nasimmobilier.devis.quotes.application.QuoteTemplateService;
import com.nasimmobilier.devis.quotes.application.QuoteTemplateService.FromTemplateRequest;
import com.nasimmobilier.devis.quotes.application.QuoteTemplateService.TemplateRequest;
import com.nasimmobilier.devis.quotes.application.QuoteTemplateService.TemplateResponse;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quote-templates")
@RequiredArgsConstructor
@Tag(name = "Modeles de devis", description = "Modeles predefinis (materiau + quantite)")
public class QuoteTemplateController {

    private final QuoteTemplateService service;

    @GetMapping
    @Operation(summary = "Lister les modeles (pagine)", description = "Tri autorise : id, name. q = recherche sur le nom.")
    public PageResponse<TemplateResponse> list(@ParameterObject PageQuery pq) {
        return service.list(pq);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un modele")
    @ApiResponse(responseCode = "404", description = "Materiau introuvable")
    @ApiResponse(responseCode = "409", description = "Nom deja utilise")
    public TemplateResponse create(@Valid @RequestBody TemplateRequest r) {
        return service.create(r);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un modele (suppression douce)")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/{id}/quotes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un devis depuis un modele", description = "Utilise les prix en vigueur aujourd'hui.")
    @ApiResponse(responseCode = "404", description = "Modele ou prix introuvable")
    public QuoteResponse createQuote(@PathVariable Long id, @Valid @RequestBody FromTemplateRequest r) {
        return service.createQuote(id, r);
    }
}
