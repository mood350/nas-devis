package com.nasimmobilier.devis.pricing.domain;

import com.nasimmobilier.devis.pricing.domain.PriceObservation.Status;
import com.nasimmobilier.devis.pricing.domain.PriceResolution.Confidence;
import com.nasimmobilier.devis.pricing.domain.PriceSelector.Candidate;
import com.nasimmobilier.devis.pricing.domain.PriceSelector.Policy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceSelectorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 6, 30);
    private static final Policy POLICY = new Policy(90, 2, true, false, 3, new BigDecimal("25"));

    private static Candidate c(long id, long supplier, String price) {
        return new Candidate(id, supplier, "Lomé", "CPJ 42,5", TODAY.minusDays(5), Status.VALIDATED, false, new BigDecimal(price));
    }

    private static PriceResolution select(List<Candidate> list) {
        return PriceSelector.select("CPJ 42,5", "Lomé", TODAY, list, POLICY);
    }

    private static String reason(PriceResolution r, long id) {
        return r.excluded().stream().filter(e -> e.observationId() == id).findFirst().orElseThrow().reason();
    }

    @Test
    void oddCountTakesTheMiddleValue() {
        var r = select(List.of(c(1, 1, "4800"), c(2, 2, "5000"), c(3, 1, "9000")));
        assertEquals(PriceResolution.Status.OK, r.status());
        assertEquals(new BigDecimal("5000.00"), r.price());
    }

    @Test
    void evenCountAveragesTheTwoMiddleValues() {
        var r = select(List.of(c(1, 1, "4000"), c(2, 2, "5000"), c(3, 1, "6000"), c(4, 2, "7000")));
        assertEquals(new BigDecimal("5500.00"), r.price());
    }

    @Test
    void medianResistsAnOutlier() {
        var r = select(List.of(c(1, 1, "5000"), c(2, 2, "5100"), c(3, 3, "90000")));
        assertEquals(new BigDecimal("5100.00"), r.price());
    }

    @Test
    void unavailableBelowMinimumNeverInventsAPrice() {
        var r = select(List.of(c(1, 1, "5000")));
        assertEquals(PriceResolution.Status.PRICE_UNAVAILABLE, r.status());
        assertNull(r.price());
        assertNull(r.confidence());
        assertTrue(select(List.of()).explanation().contains("insuffisantes"));
    }

    @Test
    void excludesEveryIncompatibleObservationWithAReason() {
        var pending = new Candidate(1L, 1L, "Lomé", "CPJ 42,5", TODAY, Status.PENDING, false, new BigDecimal("1"));
        var rejected = new Candidate(2L, 1L, "Lomé", "CPJ 42,5", TODAY, Status.REJECTED, false, new BigDecimal("1"));
        var demo = new Candidate(3L, 1L, "Lomé", "CPJ 42,5", TODAY, Status.VALIDATED, true, new BigDecimal("1"));
        var otherCity = new Candidate(4L, 1L, "Kara", "CPJ 42,5", TODAY, Status.VALIDATED, false, new BigDecimal("1"));
        var otherSpec = new Candidate(5L, 1L, "Lomé", "CPJ 32,5", TODAY, Status.VALIDATED, false, new BigDecimal("1"));
        var noUnit = new Candidate(6L, 1L, "Lomé", "CPJ 42,5", TODAY, Status.VALIDATED, false, null);
        var stale = new Candidate(7L, 1L, "Lomé", "CPJ 42,5", TODAY.minusDays(91), Status.VALIDATED, false, new BigDecimal("1"));
        var future = new Candidate(8L, 1L, "Lomé", "CPJ 42,5", TODAY.plusDays(1), Status.VALIDATED, false, new BigDecimal("1"));
        var r = select(List.of(pending, rejected, demo, otherCity, otherSpec, noUnit, stale, future));
        assertEquals(PriceResolution.Status.PRICE_UNAVAILABLE, r.status());
        assertEquals("NOT_VALIDATED", reason(r, 1));
        assertEquals("NOT_VALIDATED", reason(r, 2));
        assertEquals("DEMO_EXCLUDED", reason(r, 3));
        assertEquals("LOCALITY_MISMATCH", reason(r, 4));
        assertEquals("CHARACTERISTICS_MISMATCH", reason(r, 5));
        assertEquals("UNIT_INCOMPATIBLE", reason(r, 6));
        assertEquals("STALE", reason(r, 7));
        assertEquals("FUTURE_DATE", reason(r, 8));
    }

    @Test
    void localityAndSpecComparisonIgnoreCaseAndAccents() {
        var ok = new Candidate(1L, 1L, " LOME ", "cpj 42,5", TODAY, Status.VALIDATED, false, new BigDecimal("5000"));
        var ok2 = new Candidate(2L, 2L, "lomé", "CPJ  42,5", TODAY, Status.VALIDATED, false, new BigDecimal("5000"));
        assertEquals(PriceResolution.Status.OK, select(List.of(ok, ok2)).status());
    }

    @Test
    void confidenceDependsOnCountSuppliersAndSpread() {
        assertEquals(Confidence.HIGH, select(List.of(c(1, 1, "4900"), c(2, 2, "5000"), c(3, 3, "5100"))).confidence());
        assertEquals(Confidence.MEDIUM, select(List.of(c(1, 1, "4900"), c(2, 2, "5000"))).confidence());
        assertEquals(Confidence.MEDIUM, select(List.of(c(1, 1, "3000"), c(2, 2, "5000"), c(3, 3, "9000"))).confidence()); // ecart trop grand
        assertEquals(Confidence.MEDIUM, select(List.of(c(1, 1, "4900"), c(2, 1, "5000"), c(3, 1, "5100"))).confidence()); // un seul fournisseur
        var lenient = new Policy(90, 1, true, false, 3, new BigDecimal("25"));
        assertEquals(Confidence.LOW, PriceSelector.select("CPJ 42,5", "Lomé", TODAY, List.of(c(1, 1, "5000")), lenient).confidence());
    }

    @Test
    void demoObservationsAreOnlyUsedWhenAllowedAndAreFlagged() {
        var d1 = new Candidate(1L, 1L, "Lomé", "CPJ 42,5", TODAY, Status.VALIDATED, true, new BigDecimal("5000"));
        var d2 = new Candidate(2L, 2L, "Lomé", "CPJ 42,5", TODAY, Status.VALIDATED, true, new BigDecimal("5200"));
        var allow = new Policy(90, 2, true, true, 3, new BigDecimal("25"));
        var r = PriceSelector.select("CPJ 42,5", "Lomé", TODAY, List.of(d1, d2), allow);
        assertTrue(r.demo());
        assertTrue(r.explanation().contains("DEMONSTRATION"));
    }

    @Test
    void explanationNamesProvenance() {
        var r = select(List.of(c(1, 1, "4900"), c(2, 2, "5000"), c(3, 3, "5100")));
        assertEquals(List.of(1L, 2L, 3L), r.usedObservationIds());
        assertEquals(3, r.distinctSuppliers());
        assertTrue(r.explanation().contains("3 observation(s)"));
    }
}
