package com.nasimmobilier.devis.quotes;

import com.nasimmobilier.devis.catalog.application.MaterialService;
import com.nasimmobilier.devis.catalog.application.MaterialService.MaterialRequest;
import com.nasimmobilier.devis.categories.application.CategoryService;
import com.nasimmobilier.devis.categories.application.CategoryService.CategoryRequest;
import com.nasimmobilier.devis.pricing.application.PriceObservationService;
import com.nasimmobilier.devis.pricing.application.PriceObservationService.ObservationRequest;
import com.nasimmobilier.devis.pricing.application.PriceResolutionService;
import com.nasimmobilier.devis.pricing.domain.PriceObservation;
import com.nasimmobilier.devis.pricing.domain.PriceResolution;
import com.nasimmobilier.devis.quotes.application.QuoteService;
import com.nasimmobilier.devis.quotes.application.QuoteService.LineRequest;
import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteRequest;
import com.nasimmobilier.devis.quotes.application.QuoteService.QuoteResponse;
import com.nasimmobilier.devis.quotes.domain.Quote;
import com.nasimmobilier.devis.quotes.domain.QuoteRepository;
import com.nasimmobilier.devis.shared.NotFoundException;
import com.nasimmobilier.devis.suppliers.application.SupplierService;
import com.nasimmobilier.devis.suppliers.application.SupplierService.SupplierRequest;
import com.nasimmobilier.devis.units.application.UnitService;
import com.nasimmobilier.devis.units.application.UnitService.UnitRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contrat transactionnel/SQL partage : execute sur H2 (QuoteDbContractH2Test) ET sur un vrai PostgreSQL
 * (QuoteDbContractPostgresTest, Testcontainers, ignore sans Docker).
 */
@SpringBootTest
abstract class QuoteDbContractTest {

    @Autowired QuoteService quotes;
    @Autowired QuoteRepository quoteRepository;
    @Autowired MaterialService materials;
    @Autowired CategoryService categories;
    @Autowired UnitService units;
    @Autowired SupplierService suppliers;
    @Autowired PriceObservationService observations;
    @Autowired PriceResolutionService resolution;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager txManager;

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static QuoteRequest manualQuote(String client) {
        return new QuoteRequest(client, null, "Projet", "Lomé", null, null, null, null, null,
                List.of(new LineRequest(null, "Main d'œuvre", "f", new BigDecimal("2"), new BigDecimal("1500"), null)));
    }

    private long materialWithObservations(String... prices) {
        long cat = categories.create(new CategoryRequest("c-" + uid())).id();
        long unit = units.create(new UnitRequest("u" + uid(), "unit")).id();
        long mat = materials.create(new MaterialRequest("R-" + uid(), "Matériau", cat, unit, "C1", true)).id();
        for (String p : prices) {
            long sup = suppliers.create(new SupplierRequest("S-" + uid(), null, null, null)).id();
            long obs = observations.create(new ObservationRequest(mat, sup, "Lomé", null, new BigDecimal(p), LocalDate.now(),
                    null, "C1", "test")).id();
            observations.validate(obs);
        }
        return mat;
    }

    @Test
    void concurrentCreationsGetDistinctNumbers() throws Exception {
        int n = 16;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<QuoteResponse>> futures = new ArrayList<>();
            CountDownLatch start = new CountDownLatch(1);
            for (int i = 0; i < n; i++) {
                String client = "client-" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return quotes.create(manualQuote(client));
                }));
            }
            start.countDown();
            Set<String> numbers = new HashSet<>();
            for (Future<QuoteResponse> f : futures) numbers.add(f.get(60, TimeUnit.SECONDS).number());
            assertEquals(n, numbers.size(), "numeros uniques : " + numbers);
            assertTrue(numbers.stream().allMatch(s -> s != null && s.matches("DEV-\\d{4}-\\d{6}")));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void failedCreationRollsBackQuoteAndLines() {
        long quotesBefore = jdbc.queryForObject("select count(*) from quotes", Long.class);
        long linesBefore = jdbc.queryForObject("select count(*) from quote_lines", Long.class);
        QuoteRequest bad = new QuoteRequest("rollback", null, "P", "Lomé", null, null, null, null, null,
                List.of(new LineRequest(null, "ok", "u", BigDecimal.ONE, BigDecimal.TEN, null),
                        new LineRequest(999_999L, null, null, BigDecimal.ONE, null, null)));
        assertThrows(NotFoundException.class, () -> quotes.create(bad));
        assertEquals(quotesBefore, jdbc.queryForObject("select count(*) from quotes", Long.class));
        assertEquals(linesBefore, jdbc.queryForObject("select count(*) from quote_lines", Long.class));
    }

    @Test
    void concurrentModificationIsDetectedByOptimisticLocking() throws Exception {
        long id = quotes.create(manualQuote("lock")).id();
        long lineId = quotes.get(id).lines().get(0).id();
        ExecutorService other = Executors.newSingleThreadExecutor();
        try {
            assertThrows(ObjectOptimisticLockingFailureException.class, () -> new TransactionTemplate(txManager).executeWithoutResult(s -> {
                Quote mine = quoteRepository.findWithLinesById(id).orElseThrow();
                try { // un autre utilisateur modifie et valide entre-temps
                    other.submit(() -> quotes.changeQuantity(id, lineId, new QuoteService.QuantityRequest(new BigDecimal("9")), null)).get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
                mine.changeQuantity(lineId, new BigDecimal("5"));
                quoteRepository.flush();
            }));
            assertEquals(0, new BigDecimal("9").compareTo(quotes.get(id).lines().get(0).quantity()), "la 1re modification est conservee");
        } finally {
            other.shutdownNow();
        }
    }

    @Test
    void snapshotsAreStoredInSqlAndSurviveCatalogAndPriceChanges() {
        long mat = materialWithObservations("4800", "5000", "5200");
        QuoteResponse q = quotes.create(new QuoteRequest("snap", null, "P", "Lomé", null, null, null, null, null,
                List.of(new LineRequest(mat, null, null, new BigDecimal("3"), null, null))));
        Map<String, Object> row = jdbc.queryForMap("select designation, unit_price, line_total, price_source, price_confidence, price_basis from quote_lines where quote_id = ?", q.id());
        assertEquals("Matériau", row.get("designation"));
        assertEquals(0, new BigDecimal("5000").compareTo((BigDecimal) row.get("unit_price")));
        assertEquals(0, new BigDecimal("15000").compareTo((BigDecimal) row.get("line_total")));
        assertEquals("CATALOG", row.get("price_source"));
        assertNotNull(row.get("price_basis"));

        materials.update(mat, new MaterialRequest("R-" + uid(), "Renommé", materials.get(mat).categoryId(), materials.get(mat).unitId(), "C1", true));
        long sup = suppliers.create(new SupplierRequest("S-" + uid(), null, null, null)).id();
        for (int i = 0; i < 3; i++)
            observations.validate(observations.create(new ObservationRequest(mat, sup, "Lomé", null, new BigDecimal("99999"), LocalDate.now(), null, "C1", "t")).id());
        QuoteResponse reloaded = quotes.get(q.id());
        assertEquals("Matériau", reloaded.lines().get(0).designation());
        assertEquals(0, new BigDecimal("5000").compareTo(reloaded.lines().get(0).unitPrice()));
        assertEquals(0, new BigDecimal("15000").compareTo(reloaded.totalTtc()));
    }

    @Test
    void medianResolutionWorksOnTheRealDatabase() {
        long mat = materialWithObservations("4000", "5000", "9000");
        PriceResolution r = resolution.resolve(mat, "lome", LocalDate.now(), PriceObservation.SourceType.COMMERCIAL);
        assertEquals(PriceResolution.Status.OK, r.status());
        assertEquals(0, new BigDecimal("5000").compareTo(r.price()));
        assertEquals(PriceResolution.Status.PRICE_UNAVAILABLE,
                resolution.resolve(mat, "Lomé", LocalDate.now().plusDays(200), PriceObservation.SourceType.COMMERCIAL).status());
    }

    @Test
    void sqlConstraintsRejectInvalidData() {
        long id = quotes.create(manualQuote("constraints")).id();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into quote_lines (quote_id, line_order, designation, unit_label, quantity, unit_price, line_total, price_source, demo_price) values (?, 9, 'x', 'u', 0, 1, 0, 'MANUAL', false)", id));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into price_observations (material_id, locality, sale_unit_id, unit_price, observed_on, source_type, status, demo, created_at) "
                        + "select m.id, 'x', m.unit_id, -1, current_date, 'COMMERCIAL', 'PENDING', false, current_timestamp from materials m fetch first 1 rows only"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update quotes set discount_percent = 150 where id = ?", id));
        String other = quotes.create(manualQuote("other")).number();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("update quotes set quote_number = ? where id = ?", other, id));
    }
}
