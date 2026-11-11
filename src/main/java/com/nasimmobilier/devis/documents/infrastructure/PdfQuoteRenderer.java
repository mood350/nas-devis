package com.nasimmobilier.devis.documents.infrastructure;

import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteResponse;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Locale;

/** Thymeleaf (templates/quotes/quote.html, A4) -> PDF via OpenHTMLtoPDF. Entierement derive du snapshot du devis. */
@Component
@RequiredArgsConstructor
public class PdfQuoteRenderer {

    private final TemplateEngine templateEngine;
    private final LogoProvider logo;

    public byte[] render(QuoteResponse quote) {
        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("quote", quote);
        ctx.setVariable("logo", logo.bytes().map(b -> "data:image/png;base64," + Base64.getEncoder().encodeToString(b)).orElse(null));
        String html = XmlChars.clean(templateEngine.process("quotes/quote", ctx));
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder b = new PdfRendererBuilder();
            b.useFastMode();
            b.withHtmlContent(html, null);
            b.toStream(out);
            b.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Echec de generation du PDF", e);
        }
    }
}
