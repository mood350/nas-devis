package com.nasimmobilier.devis.quotes.presentation;

import com.nasimmobilier.devis.quotes.application.QuoteService;
import com.nasimmobilier.devis.quotes.application.QuoteService.*;
import com.nasimmobilier.devis.quotes.domain.Quote;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/quotes")
@RequiredArgsConstructor
@Tag(name = "Devis", description = "Creation, edition des brouillons et cycle de vie (DRAFT -> ISSUED)")
public class QuoteController {

    private final QuoteService service;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Creer un devis", description = "Calcule lignes, remise, taxes et totaux. Prix, taxes et identite de l'entreprise sont figes (snapshot).")
    @ApiResponse(responseCode = "400", description = "Requete invalide")
    @ApiResponse(responseCode = "404", description = "Materiau ou taux introuvable")
    @ApiResponse(responseCode = "422", description = "PRICE_UNAVAILABLE, taux non valide, materiau inactif, remise > maximum")
    public QuoteResponse create(@Valid @RequestBody QuoteRequest r) {
        return service.create(r);
    }

    @GetMapping
    @Operation(summary = "Rechercher les devis (q : numero, client, projet ; tri : id, issueDate, clientName, totalTtc, status)")
    public PageResponse<QuoteSummary> list(@RequestParam(required = false) Quote.Status status, @ParameterObject PageQuery pq) {
        return service.search(status, pq);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulter un devis (lignes, taxes, historique des statuts)")
    @ApiResponse(responseCode = "404", description = "Devis introuvable")
    public QuoteResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier l'en-tete d'un brouillon (les lignes ne sont pas touchees)")
    @ApiResponse(responseCode = "409", description = "Modification concurrente")
    @ApiResponse(responseCode = "422", description = "Devis emis : immuable")
    public QuoteResponse update(@PathVariable Long id, @Valid @RequestBody QuoteRequest r,
                                @Parameter(description = "Version du devis vue par l'utilisateur ; 409 si le brouillon a change depuis") @RequestParam(required = false) Long version) {
        return service.updateHeader(id, r, version);
    }

    @PostMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ajouter une ligne a un brouillon")
    @ApiResponse(responseCode = "422", description = "Devis emis, PRICE_UNAVAILABLE, taux non valide")
    public QuoteResponse addLine(@PathVariable Long id, @Valid @RequestBody LineRequest l,
                                 @Parameter(description = "Taxe par defaut si la ligne n'a pas de taxCode") @RequestParam(required = false) String defaultTaxCode,
                                 @Parameter(description = "Version du devis vue par l'utilisateur ; 409 si le brouillon a change depuis") @RequestParam(required = false) Long version) {
        return service.addLine(id, l, defaultTaxCode, version);
    }

    @PutMapping("/{id}/lines/{lineId}")
    @Operation(summary = "Modifier la quantite d'une ligne d'un brouillon")
    @ApiResponse(responseCode = "404", description = "Devis ou ligne introuvable")
    @ApiResponse(responseCode = "422", description = "Devis emis")
    public QuoteResponse changeQuantity(@PathVariable Long id, @PathVariable Long lineId, @Valid @RequestBody QuantityRequest r,
                                        @Parameter(description = "Version du devis vue par l'utilisateur ; 409 si le brouillon a change depuis") @RequestParam(required = false) Long version) {
        return service.changeQuantity(id, lineId, r, version);
    }

    @DeleteMapping("/{id}/lines/{lineId}")
    @Operation(summary = "Supprimer une ligne d'un brouillon")
    @ApiResponse(responseCode = "422", description = "Devis emis")
    public QuoteResponse removeLine(@PathVariable Long id, @PathVariable Long lineId, @Parameter(description = "Version du devis vue par l'utilisateur ; 409 si le brouillon a change depuis") @RequestParam(required = false) Long version) {
        return service.removeLine(id, lineId, version);
    }

    @PostMapping("/{id}/reprice")
    @Operation(summary = "Re-selectionner les prix catalogue d'un brouillon (prix manuels conserves)")
    @ApiResponse(responseCode = "422", description = "Devis emis ou PRICE_UNAVAILABLE (aucune modification)")
    public QuoteResponse reprice(@PathVariable Long id) {
        return service.reprice(id);
    }

    @PostMapping("/{id}/issue")
    @Operation(summary = "Emettre un devis", description = "Le devis devient immuable. Refuse s'il est vide ou contient des prix DEMO.")
    @ApiResponse(responseCode = "409", description = "Modification concurrente")
    @ApiResponse(responseCode = "422", description = "Deja emis, vide ou prix de demonstration")
    public QuoteResponse issue(@PathVariable Long id, @Parameter(description = "Version du devis vue par l'utilisateur ; 409 si le brouillon a change depuis") @RequestParam(required = false) Long version) {
        return service.issue(id, version);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un devis brouillon")
    @ApiResponse(responseCode = "422", description = "Devis deja emis")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
