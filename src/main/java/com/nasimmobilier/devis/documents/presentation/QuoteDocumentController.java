package com.nasimmobilier.devis.documents.presentation;

import com.nasimmobilier.devis.documents.infrastructure.DocxQuoteRenderer;
import com.nasimmobilier.devis.documents.infrastructure.PdfQuoteRenderer;
import com.nasimmobilier.devis.quotes.application.QuoteService;
import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quotes/{id}")
@RequiredArgsConstructor
@Tag(name = "Documents", description = "Export des devis en PDF et DOCX")
public class QuoteDocumentController {

    private static final MediaType DOCX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    // Volontairement sans @Transactional : QuoteService.get lit le devis dans sa propre transaction read-only,
    // puis le rendu lourd s'execute sans connexion SQL ouverte.
    private final QuoteService quotes;
    private final PdfQuoteRenderer pdfRenderer;
    private final DocxQuoteRenderer docxRenderer;

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Exporter le devis en PDF (A4)")
    @ApiResponse(responseCode = "200", description = "Fichier PDF")
    @ApiResponse(responseCode = "404", description = "Devis introuvable")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        QuoteResponse q = quotes.get(id);
        return file(pdfRenderer.render(q), MediaType.APPLICATION_PDF, name(q, "pdf"));
    }

    @GetMapping(value = "/docx", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    @Operation(summary = "Exporter le devis en DOCX")
    @ApiResponse(responseCode = "200", description = "Fichier Word")
    @ApiResponse(responseCode = "404", description = "Devis introuvable")
    public ResponseEntity<byte[]> docx(@PathVariable Long id) {
        QuoteResponse q = quotes.get(id);
        return file(docxRenderer.render(q), DOCX, name(q, "docx"));
    }

    private static String name(QuoteResponse q, String ext) {
        return q.number() + "." + ext;
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
