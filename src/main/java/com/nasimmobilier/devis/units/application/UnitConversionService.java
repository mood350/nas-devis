package com.nasimmobilier.devis.units.application;

import com.nasimmobilier.devis.shared.BusinessRuleException;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import com.nasimmobilier.devis.units.domain.UnitConversion;
import com.nasimmobilier.devis.units.domain.UnitConversionRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class UnitConversionService {

    public record ConversionRequest(@NotNull Long fromUnitId, @NotNull Long toUnitId,
                                    @Schema(description = "1 unite source = factor unites cible", example = "1000")
                                    @NotNull @DecimalMin(value = "0.000001") BigDecimal factor) {}

    public record ConversionResponse(Long id, Long fromUnitId, Long toUnitId, BigDecimal factor) {}

    private final UnitConversionRepository repository;
    private final UnitService units;

    public ConversionResponse create(ConversionRequest r) {
        if (r.fromUnitId().equals(r.toUnitId())) throw new BusinessRuleException("Les deux unites doivent differer");
        units.find(r.fromUnitId());
        units.find(r.toUnitId());
        UnitConversion c = new UnitConversion();
        c.setFromUnitId(r.fromUnitId());
        c.setToUnitId(r.toUnitId());
        c.setFactor(r.factor());
        return toResponse(repository.saveAndFlush(c));
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversionResponse> list(PageQuery pq) {
        return PageResponse.of(repository.findAll(pq.pageable(Set.of("id", "fromUnitId", "toUnitId"), Sort.by("id"))),
                this::toResponse);
    }

    /** Nombre d'unites "to" dans une unite "from" ; vide si aucune conversion declaree (sens direct ou inverse). */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> factor(Long fromUnitId, Long toUnitId) {
        if (fromUnitId.equals(toUnitId)) return Optional.of(BigDecimal.ONE);
        Optional<BigDecimal> direct = repository.findByFromUnitIdAndToUnitId(fromUnitId, toUnitId).map(UnitConversion::getFactor);
        if (direct.isPresent()) return direct;
        return repository.findByFromUnitIdAndToUnitId(toUnitId, fromUnitId)
                .map(c -> BigDecimal.ONE.divide(c.getFactor(), 12, RoundingMode.HALF_UP));
    }

    private ConversionResponse toResponse(UnitConversion c) {
        return new ConversionResponse(c.getId(), c.getFromUnitId(), c.getToUnitId(), c.getFactor());
    }
}
