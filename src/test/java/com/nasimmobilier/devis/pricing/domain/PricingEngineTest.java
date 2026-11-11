package com.nasimmobilier.devis.pricing.domain;

import com.nasimmobilier.devis.pricing.domain.PricingEngine.Taxed;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import com.nasimmobilier.devis.shared.BusinessRuleException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PricingEngineTest {

    private static BigDecimal d(String s) {
        return new BigDecimal(s);
    }

    private static Taxed untaxed(String amount) {
        return new Taxed(d(amount), null, null, null);
    }

    private static Taxed taxed(String amount, String code, String rate) {
        return new Taxed(d(amount), code, code, d(rate));
    }

    @Test
    void lineTotalRoundsHalfUp() {
        assertEquals(d("4.16"), PricingEngine.lineTotal(d("0.333"), d("12.50")));
    }

    @Test
    void amountsBeyondTheColumnSizeAreRejected() {
        assertThrows(BusinessRuleException.class, () -> PricingEngine.lineTotal(d("999999999999"), d("9999999999999")));
        // deux lignes valides dont la somme depasse 10^13
        assertThrows(BusinessRuleException.class, () -> PricingEngine.totals(List.of(untaxed("9000000000000.00"), untaxed("9000000000000.00")),
                BigDecimal.ZERO, BigDecimal.ZERO, null));
        assertEquals(d("9999999999999.99"), PricingEngine.lineTotal(d("1"), d("9999999999999.99")));
    }

    @Test
    void noTaxConfiguredMeansNoTaxAtAll() {
        var t = PricingEngine.totals(List.of(untaxed("1000.00"), untaxed("500.00")), d("10"), BigDecimal.ZERO, null);
        assertEquals(d("1500.00"), t.subtotal());
        assertEquals(d("150.00"), t.discount());
        assertEquals(d("1350.00"), t.netHt());
        assertTrue(t.taxes().isEmpty());
        assertEquals(d("0.00"), t.taxTotal());
        assertEquals(d("1350.00"), t.totalTtc());
    }

    @Test
    void discountIsAppliedBeforeTaxAndTransportIsNeverDiscounted() {
        // lignes taxees 1000 + non taxee 500, remise 10 %, transport 100 taxe aussi, taux 20 %
        var t = PricingEngine.totals(List.of(taxed("1000.00", "T20", "20"), untaxed("500.00")), d("10"),
                d("100.00"), taxed("100.00", "T20", "20"));
        assertEquals(d("1500.00"), t.subtotal());
        assertEquals(d("150.00"), t.discount());                 // 100 + 50
        assertEquals(d("100.00"), t.transport());
        assertEquals(d("1450.00"), t.netHt());                   // 900 + 100 + 450
        assertEquals(1, t.taxes().size());
        assertEquals(d("1000.00"), t.taxes().get(0).base());     // 1000 - 100 + 100
        assertEquals(d("200.00"), t.taxes().get(0).amount());
        assertEquals(d("1650.00"), t.totalTtc());
    }

    @Test
    void severalRatesAreBrokenDownSeparately() {
        var t = PricingEngine.totals(List.of(taxed("100.00", "A", "10"), taxed("200.00", "B", "5")), BigDecimal.ZERO,
                BigDecimal.ZERO, null);
        assertEquals(2, t.taxes().size());
        assertEquals(d("10.00"), t.taxes().get(0).amount());
        assertEquals(d("10.00"), t.taxes().get(1).amount());
        assertEquals(d("320.00"), t.totalTtc());
    }

    @Test
    void transportWithoutTaxStaysUntaxed() {
        var t = PricingEngine.totals(List.of(taxed("100.00", "A", "10")), BigDecimal.ZERO, d("50"), null);
        assertEquals(d("160.00"), t.totalTtc());                 // 100 + 10 + 50
    }
}
