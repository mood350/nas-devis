package com.nasimmobilier.devis.shared;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

/**
 * Parametres de pagination communs : page (0..), size (1..100), sort ("champ,asc|desc"), q (recherche texte).
 * Le tri n'est accepte que sur une liste blanche de champs (sinon HTTP 400).
 */
public record PageQuery(
        @Schema(description = "Numero de page (0..)", example = "0") Integer page,
        @Schema(description = "Taille de page (1..100)", example = "20") Integer size,
        @Schema(description = "Tri : champ,asc|desc", example = "name,asc") String sort,
        @Schema(description = "Recherche texte") String q) {

    public Pageable pageable(Set<String> allowedSortFields, Sort defaultSort) {
        int p = page == null ? 0 : Math.max(page, 0);
        int s = size == null ? 20 : Math.min(Math.max(size, 1), 100);
        if (sort == null || sort.isBlank()) return PageRequest.of(p, s, defaultSort);
        String[] parts = sort.split(",");
        if (!allowedSortFields.contains(parts[0].trim()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tri non supporte : " + parts[0] + " (autorises : " + allowedSortFields + ")");
        Sort.Direction dir = parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        // id en second critere : pagination deterministe meme avec des valeurs identiques
        return PageRequest.of(p, s, Sort.by(dir, parts[0].trim()).and(Sort.by("id")));
    }

    public String search() {
        return q == null ? "" : q.trim();
    }
}
