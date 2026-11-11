package com.nasimmobilier.devis.quotes.application;

import com.nasimmobilier.devis.catalog.application.MaterialService;
import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteRequest;
import com.nasimmobilier.devis.quotes.application.QuoteService.LineRequest;
import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteResponse;
import com.nasimmobilier.devis.quotes.domain.QuoteTemplate;
import com.nasimmobilier.devis.quotes.domain.QuoteTemplateRepository;
import com.nasimmobilier.devis.shared.NotFoundException;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class QuoteTemplateService {

    public record ItemRequest(@NotNull Long materialId,
                              @Schema(example = "10") @NotNull @DecimalMin("0.001") @Digits(integer = 12, fraction = 3) BigDecimal quantity) {}

    public record TemplateRequest(@Schema(example = "Mur de cloture 20 m") @NotBlank @Size(max = 150) String name,
                                  @Size(max = 255) String description,
                                  @NotEmpty @Valid List<ItemRequest> items) {}

    public record TemplateResponse(Long id, String name, String description, List<ItemRequest> items) {}

    public record FromTemplateRequest(@NotBlank @Size(max = 200) String clientName,
                                      @Size(max = 255) String clientAddress,
                                      @NotBlank @Size(max = 255) String projectTitle,
                                      @NotBlank @Size(max = 255) String siteLocation,
                                      @Min(1) @Max(365) Integer validityDays,
                                      @DecimalMin("0") @DecimalMax("100") BigDecimal discountPercent,
                                      @DecimalMin("0") @Digits(integer = 13, fraction = 2) BigDecimal transportCost,
                                      @Size(max = 30) String transportTaxCode,
                                      @Size(max = 30) String taxCode) {}

    private final QuoteTemplateRepository repository;
    private final MaterialService materials;
    private final QuoteService quotes;

    public TemplateResponse create(TemplateRequest r) {
        QuoteTemplate t = new QuoteTemplate();
        t.setName(r.name());
        t.setDescription(r.description());
        for (ItemRequest i : r.items()) {
            materials.find(i.materialId());
            t.addItem(i.materialId(), i.quantity());
        }
        return toResponse(repository.saveAndFlush(t));
    }

    @Transactional(readOnly = true)
    public PageResponse<TemplateResponse> list(PageQuery pq) {
        return PageResponse.of(repository.findByNameContainingIgnoreCase(pq.search(),
                pq.pageable(Set.of("id", "name"), Sort.by("name"))), this::toResponse);
    }

    public void delete(Long id) {
        repository.findById(id).orElseThrow(() -> new NotFoundException("Modele", id)).softDelete();
    }

    /** Instancie le modele aux prix resolus aujourd'hui, dans une seule transaction. */
    public QuoteResponse createQuote(Long templateId, FromTemplateRequest r) {
        QuoteTemplate t = repository.findWithItemsById(templateId).orElseThrow(() -> new NotFoundException("Modele", templateId));
        List<LineRequest> lines = t.getItems().stream()
                .map(i -> new LineRequest(i.getMaterialId(), null, null, i.getQuantity(), null, null)).toList();
        return quotes.create(new QuoteRequest(r.clientName(), r.clientAddress(), r.projectTitle(), r.siteLocation(),
                r.validityDays(), r.discountPercent(), r.transportCost(), r.transportTaxCode(), r.taxCode(), lines));
    }

    private TemplateResponse toResponse(QuoteTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getDescription(),
                t.getItems().stream().map(i -> new ItemRequest(i.getMaterialId(), i.getQuantity())).toList());
    }
}
