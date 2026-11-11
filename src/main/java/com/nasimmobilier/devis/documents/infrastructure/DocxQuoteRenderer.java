package com.nasimmobilier.devis.documents.infrastructure;

import com.nasimmobilier.devis.quotes.application.QuoteService.CompanyInfo;
import com.nasimmobilier.devis.quotes.application.QuoteService.LineResponse;
import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteResponse;
import com.nasimmobilier.devis.quotes.application.QuoteService.TaxResponse;
import lombok.RequiredArgsConstructor;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Stream;

/** Quote -> DOCX editable dans Word (vrais tableaux, en-tete de tableau repete). Derive du snapshot du devis. */
@Component
@RequiredArgsConstructor
public class DocxQuoteRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final LogoProvider logo;

    public byte[] render(QuoteResponse q) {
        try (XWPFDocument doc = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            header(doc, q.company());
            paragraph(doc, "DEVIS " + q.number(), true, 16);
            paragraph(doc, "Établi le " + q.issueDate().format(DATE) + " - valable jusqu'au " + q.validUntil().format(DATE)
                    + " (" + q.validityDays() + " jours)", false, 10);
            paragraph(doc, "Client : " + q.clientName() + (q.clientAddress() == null ? "" : " - " + q.clientAddress()), false, 11);
            paragraph(doc, "Chantier : " + q.siteLocation(), false, 11);
            paragraph(doc, "Objet : " + q.projectTitle(), true, 11);
            lines(doc, q);
            paragraph(doc, "", false, 6);
            totals(doc, q);
            paragraph(doc, "", false, 14);
            signatures(doc, q.company());
            if (q.containsDemoPrices()) paragraph(doc, "DOCUMENT DE DÉMONSTRATION - prix fictifs, sans valeur commerciale.", true, 10);
            doc.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Echec de generation du DOCX", e);
        }
    }

    private void header(XWPFDocument doc, CompanyInfo c) throws Exception {
        XWPFTable t = doc.createTable(1, 2);
        clearBorders(t);
        var logoBytes = logo.bytes();
        if (logoBytes.isPresent())
            t.getRow(0).getCell(0).getParagraphs().get(0).createRun()
                    .addPicture(new ByteArrayInputStream(logoBytes.get()), Document.PICTURE_TYPE_PNG, "logo.png", Units.toEMU(60), Units.toEMU(60));
        XWPFTableCell cell = t.getRow(0).getCell(1);
        run(cell.getParagraphs().get(0), c.name(), true, 18);
        Stream.of(c.tagline(), c.address(), c.phone().isEmpty() ? "" : "Tél : " + c.phone(), c.email(),
                        c.nif().isEmpty() ? "" : "NIF : " + c.nif())
                .filter(s -> !s.isBlank()).forEach(s -> run(cell.addParagraph(), s, false, 9));
    }

    private void lines(XWPFDocument doc, QuoteResponse q) {
        XWPFTable t = doc.createTable(1, 6);
        String[] head = {"N°", "Désignation", "Unité", "Quantité", "P.U. (FCFA)", "Total HT (FCFA)"};
        XWPFTableRow header = t.getRow(0);
        header.setRepeatHeader(true);
        for (int i = 0; i < head.length; i++) setCell(header.getCell(i), head[i], true);
        for (LineResponse l : q.lines()) {
            XWPFTableRow row = t.createRow();
            setCell(row.getCell(0), String.valueOf(l.order()), false);
            setCell(row.getCell(1), l.designation(), false);
            setCell(row.getCell(2), l.unit(), false);
            setCell(row.getCell(3), num(l.quantity(), 3), false);
            setCell(row.getCell(4), num(l.unitPrice(), 2), false);
            setCell(row.getCell(5), num(l.lineTotal(), 2), false);
        }
    }

    private void totals(XWPFDocument doc, QuoteResponse q) {
        paragraph(doc, "Sous-total HT : " + num(q.subtotal(), 2) + " FCFA", false, 11);
        if (q.discountAmount().signum() > 0)
            paragraph(doc, "Remise (" + q.discountPercent() + " %) : -" + num(q.discountAmount(), 2) + " FCFA", false, 11);
        if (q.transportCost().signum() > 0)
            paragraph(doc, "Frais de transport HT : " + num(q.transportCost(), 2) + " FCFA", false, 11);
        paragraph(doc, "Total net HT : " + num(q.netHt(), 2) + " FCFA", true, 11);
        if (q.taxes().isEmpty()) paragraph(doc, "Aucune taxe appliquée.", false, 11);
        for (TaxResponse t : q.taxes())
            paragraph(doc, t.label() + " (" + t.rate() + " %) sur " + num(t.base(), 2) + " : " + num(t.amount(), 2) + " FCFA", false, 11);
        paragraph(doc, "TOTAL GÉNÉRAL TTC : " + num(q.totalTtc(), 2) + " FCFA", true, 13);
    }

    private void signatures(XWPFDocument doc, CompanyInfo c) {
        XWPFTable t = doc.createTable(1, 2);
        XWPFTableRow row = t.getRow(0);
        row.setHeight(1800);
        setCell(row.getCell(0), "Bon pour accord - le client\n(date, signature)", false);
        setCell(row.getCell(1), c.name() + "\nCachet et signature", false);
    }

    private static void clearBorders(XWPFTable t) {
        t.setInsideHBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setInsideVBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setTopBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setBottomBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setLeftBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
        t.setRightBorder(XWPFTable.XWPFBorderType.NONE, 0, 0, "FFFFFF");
    }

    private static void setCell(XWPFTableCell cell, String text, boolean bold) {
        XWPFParagraph p = cell.getParagraphs().get(0);
        XWPFRun r = p.createRun();
        r.setBold(bold);
        r.setFontSize(10);
        String[] parts = text.split("\n", -1);
        for (int i = 0; i < parts.length; i++) {
            r.setText(parts[i]);
            if (i < parts.length - 1) r.addBreak();
        }
    }

    private static void run(XWPFParagraph p, String text, boolean bold, int size) {
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setBold(bold);
        r.setFontSize(size);
    }

    private static void paragraph(XWPFDocument doc, String text, boolean bold, int size) {
        run(doc.createParagraph(), text, bold, size);
    }

    private static String num(BigDecimal v, int decimals) {
        DecimalFormatSymbols s = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        s.setGroupingSeparator(' '); // l'espace insecable de Locale.FRANCE s'affiche mal selon les polices
        return new DecimalFormat(decimals == 0 ? "#,##0" : "#,##0." + "0".repeat(decimals), s).format(v);
    }
}
