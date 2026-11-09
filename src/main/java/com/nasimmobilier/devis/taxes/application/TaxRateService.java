package com.nasimmobilier.devis.taxes.application;

import com.nasimmobilier.devis.shared.BusinessRuleException;
import com.nasimmobilier.devis.shared.NotFoundException;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import com.nasimmobilier.devis.taxes.domain.TaxRate;
import com.nasimmobilier.devis.taxes.domain.TaxRateRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class TaxRateService {

    public record TaxRateRequest(@Schema(description = "Identifiant libre choisi par l'entreprise", example = "TVA") @NotBlank @Size(max = 30) String code,
                                 @Schema(example = "Taux a confirmer avec le comptable") @NotBlank @Size(max = 100) String label,
                                 @Schema(description = "Taux en % a faire valider avant usage", example = "0") @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal ratePercent) {}

    public record TaxRateResponse(Long id, String code, String label, BigDecimal ratePercent, boolean validated, LocalDateTime validatedAt) {}

    private final TaxRateRepository repository;

    public TaxRateResponse create(TaxRateRequest r) {
        TaxRate t = new TaxRate();
        t.setCode(r.code());
        t.setLabel(r.label());
        t.setRatePercent(r.ratePercent());
        return toResponse(repository.saveAndFlush(t));
    }

    public TaxRateResponse validate(String code) {
        TaxRate t = find(code);
        if (!t.isValidated()) {
            t.setValidated(true);
            t.setValidatedAt(LocalDateTime.now());
        }
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public PageResponse<TaxRateResponse> list(PageQuery pq) {
        return PageResponse.of(repository.findByCodeContainingIgnoreCaseOrLabelContainingIgnoreCase(pq.search(), pq.search(),
                pq.pageable(Set.of("id", "code", "ratePercent"), Sort.by("code"))), this::toResponse);
    }

    /** Taux utilisable dans un devis : doit exister et avoir ete valide. Un code nul = aucune taxe. */
    @Transactional(readOnly = true)
    public TaxRate applicable(String code) {
        if (code == null || code.isBlank()) return null;
        TaxRate t = find(code);
        if (!t.isValidated()) throw new BusinessRuleException("Taux fiscal non valide : " + code);
        return t;
    }

    private TaxRate find(String code) {
        return repository.findByCode(code).orElseThrow(() -> new NotFoundException("Taux fiscal", code));
    }

    private TaxRateResponse toResponse(TaxRate t) {
        return new TaxRateResponse(t.getId(), t.getCode(), t.getLabel(), t.getRatePercent(), t.isValidated(), t.getValidatedAt());
    }
}
