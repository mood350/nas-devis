package com.nasimmobilier.devis.units.application;

import com.nasimmobilier.devis.shared.NotFoundException;
import com.nasimmobilier.devis.units.domain.MeasureUnit;
import com.nasimmobilier.devis.units.domain.MeasureUnitRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.nasimmobilier.devis.shared.PageQuery;
import com.nasimmobilier.devis.shared.PageResponse;
import org.springframework.data.domain.Sort;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
@Transactional
public class UnitService {

    public record UnitRequest(@Schema(example = "m3") @NotBlank @Size(max = 20) String code,
                              @Schema(example = "Metre cube") @NotBlank @Size(max = 100) String name) {}

    public record UnitResponse(Long id, String code, String name) {}

    private final MeasureUnitRepository repository;

    public UnitResponse create(UnitRequest r) {
        MeasureUnit u = new MeasureUnit();
        u.setCode(r.code());
        u.setName(r.name());
        return toResponse(repository.saveAndFlush(u));
    }

    public UnitResponse update(Long id, UnitRequest r) {
        MeasureUnit u = find(id);
        u.setCode(r.code());
        u.setName(r.name());
        repository.flush();
        return toResponse(u);
    }

    public void delete(Long id) {
        repository.delete(find(id));
        repository.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<UnitResponse> list(PageQuery pq) {
        return PageResponse.of(repository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(pq.search(), pq.search(),
                pq.pageable(Set.of("id", "code", "name"), Sort.by("code"))), this::toResponse);
    }

    public MeasureUnit find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Unite", id));
    }

    private UnitResponse toResponse(MeasureUnit u) {
        return new UnitResponse(u.getId(), u.getCode(), u.getName());
    }
}
