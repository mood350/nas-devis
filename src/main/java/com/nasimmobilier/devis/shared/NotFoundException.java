package com.nasimmobilier.devis.shared;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String what, Object id) {
        super(what + " introuvable : " + id);
    }
}
