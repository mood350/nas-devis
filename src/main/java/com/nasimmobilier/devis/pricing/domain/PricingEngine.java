package com.nasimmobilier.devis.pricing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calcul pur d'un devis. Ordre d'application (documente dans le README) :
 * <pre>
 * 1. total ligne      = quantite x prix unitaire
 * 2. sous-total HT    = somme des totaux de lignes
 * 3. remise           = % applique a chaque groupe fiscal (les lignes d'un meme taux), AVANT taxe
 * 4. transport        = ajoute apres remise (jamais remise), HT
 * 5. taxe par groupe  = (lignes du groupe - remise du groupe + transport du groupe) x taux ; aucun taux = aucune taxe
 * 6. total TTC        = net HT + somme des taxes
 * </pre>
 * Arrondi HALF_UP a 2 decimales a chaque montant derive. Aucun taux n'est jamais suppose.
 */
public final class PricingEngine {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    /** Montant HT et taxe qui lui est rattachee (code nul = non taxe). */
    public record Taxed(BigDecimal amount, String taxCode, String taxLabel, BigDecimal taxRate) {}

    public record TaxBreakdown(String code, String label, BigDecimal rate, BigDecimal base, BigDecimal amount) {}

    public record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal transport, BigDecimal netHt,
                         List<TaxBreakdown> taxes, BigDecimal taxTotal, BigDecimal totalTtc) {}

    private PricingEngine() {}

    public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice) {
        return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
    }

    private static final class Group {
        String label;
        BigDecimal rate;
        BigDecimal lines = BigDecimal.ZERO;
        BigDecimal transport = BigDecimal.ZERO;
    }

    public static Totals totals(List<Taxed> lines, BigDecimal discountPercent, BigDecimal transportCost, Taxed transportTax) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Taxed l : lines) {
            Group g = group(groups, l);
            g.lines = g.lines.add(l.amount());
        }
        BigDecimal transport = transportCost.setScale(2, RoundingMode.HALF_UP);
        if (transport.signum() > 0) group(groups, transportTax).transport = transport;

        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        BigDecimal subtotal = zero, discount = zero, net = zero, taxTotal = zero;
        List<TaxBreakdown> breakdown = new ArrayList<>();
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            Group g = e.getValue();
            BigDecimal groupDiscount = percent(g.lines, discountPercent);
            BigDecimal base = g.lines.subtract(groupDiscount).add(g.transport);
            subtotal = subtotal.add(g.lines);
            discount = discount.add(groupDiscount);
            net = net.add(base);
            if (!e.getKey().isEmpty()) {
                BigDecimal tax = percent(base, g.rate);
                taxTotal = taxTotal.add(tax);
                breakdown.add(new TaxBreakdown(e.getKey(), g.label, g.rate, base, tax));
            }
        }
        return new Totals(subtotal, discount, transport, net, breakdown, taxTotal, net.add(taxTotal));
    }

    private static Group group(Map<String, Group> groups, Taxed t) {
        String key = t == null || t.taxCode() == null ? "" : t.taxCode();
        Group g = groups.computeIfAbsent(key, k -> new Group());
        if (!key.isEmpty()) {
            g.label = t.taxLabel();
            g.rate = t.taxRate();
        }
        return g;
    }

    private static BigDecimal percent(BigDecimal base, BigDecimal pct) {
        return base.multiply(pct).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
}
