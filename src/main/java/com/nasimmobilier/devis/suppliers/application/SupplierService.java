package com.nasimmobilier.devis.suppliers.application;

import com.nasimmobilier.devis.shared.NotFoundException;
import com.nasimmobilier.devis.suppliers.domain.Supplier;
import com.nasimmobilier.devis.suppliers.domain.SupplierRepository;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
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
public class SupplierService {

    public record SupplierRequest(@Schema(example = "Quincaillerie Centrale Lome") @NotBlank @Size(max = 150) String name,
                                  @Schema(example = "+228 90 00 00 00") @Size(max = 50) String phone,
                                  @Schema(example = "contact@exemple.tg") @Email @Size(max = 150) String email,
                                  @Size(max = 255) String address) {}

    public record SupplierResponse(Long id, String name, String phone, String email, String address) {}

    private final SupplierRepository repository;

    public SupplierResponse create(SupplierRequest r) {
        Supplier s = new Supplier();
        apply(s, r);
        return toResponse(repository.saveAndFlush(s));
    }

    public SupplierResponse update(Long id, SupplierRequest r) {
        Supplier s = find(id);
        apply(s, r);
        repository.flush();
        return toResponse(s);
    }

    public void delete(Long id) {
        repository.delete(find(id));
        repository.flush();
    }

    @Transactional(readOnly = true)
    public PageResponse<SupplierResponse> list(PageQuery pq) {
        return PageResponse.of(repository.findByNameContainingIgnoreCase(pq.search(),
                pq.pageable(Set.of("id", "name"), Sort.by("name"))), this::toResponse);
    }

    public Supplier find(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Fournisseur", id));
    }

    private void apply(Supplier s, SupplierRequest r) {
        s.setName(r.name());
        s.setPhone(r.phone());
        s.setEmail(r.email());
        s.setAddress(r.address());
    }

    private SupplierResponse toResponse(Supplier s) {
        return new SupplierResponse(s.getId(), s.getName(), s.getPhone(), s.getEmail(), s.getAddress());
    }
}
