package com.nasimmobilier.devis.documents.infrastructure;

/** Caracteres de controle (ex. tabulation verticale collee depuis Word) : interdits en XML, ils feraient echouer le PDF et corrompre le DOCX. */
final class XmlChars {

    private XmlChars() {}

    static String clean(String s) {
        return s == null ? null : s.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]", "");
    }
}
