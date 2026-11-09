package com.nasimmobilier.devis.documents.application;

import com.nasimmobilier.devis.documents.infrastructure.DocxQuoteRenderer;
import com.nasimmobilier.devis.documents.infrastructure.PdfQuoteRenderer;
import com.nasimmobilier.devis.quotes.application.QuoteService;
import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Volontairement NON transactionnel : le devis est lu dans une courte transaction read-only
 * (QuoteService.get), puis le rendu lourd s'execute sans connexion SQL ouverte.
 */
@Service
@RequiredArgsConstructor
public class QuoteDocumentService {

    private final QuoteService quotes;
    private final PdfQuoteRenderer pdf;
    private final DocxQuoteRenderer docx;

    public QuoteResponse quote(Long id) {
        return quotes.get(id);
    }

    public byte[] pdf(QuoteResponse q) {
        return pdf.render(q);
    }

    public byte[] docx(QuoteResponse q) {
        return docx.render(q);
    }
}
