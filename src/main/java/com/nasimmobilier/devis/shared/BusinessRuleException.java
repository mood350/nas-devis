package com.nasimmobilier.devis.shared;

/** Violation d'une regle metier (HTTP 422). */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
