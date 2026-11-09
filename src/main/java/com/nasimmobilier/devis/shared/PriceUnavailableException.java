package com.nasimmobilier.devis.shared;

/** Aucune donnee de prix fiable : le systeme n'invente jamais de prix (HTTP 422, code PRICE_UNAVAILABLE). */
public class PriceUnavailableException extends RuntimeException {
    public PriceUnavailableException(String material, String explanation) {
        super("PRICE_UNAVAILABLE pour " + material + " : " + explanation);
    }
}
