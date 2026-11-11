package com.nasimmobilier.devis.quotes;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import com.nasimmobilier.devis.pricing.domain.PriceObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuoteFlowTest {

    @Autowired MockMvc mvc;
    @Autowired PriceObservationRepository observationRepository;
    @Autowired JdbcTemplate jdbc;

    // ---------- helpers ----------

    private String create(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    private String ok(String url, String body) throws Exception {
        return mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private String json(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private static long id(String json) {
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private long unitId(String code) throws Exception {
        return ((Number) JsonPath.<java.util.List<Object>>read(json("/api/units?size=100"), "$.content[?(@.code=='" + code + "')].id").get(0)).longValue();
    }

    /** Materiau (unite = unitCode) avec trois observations validees a Lome de prix 4800 / 5000 / 5200. */
    private long materialWithPrices(String unitCode, String saleUnitCode, String p1, String p2, String p3) throws Exception {
        long cat = id(create("/api/categories", "{\"name\":\"cat-" + uid() + "\"}"));
        long mat = id(create("/api/materials", "{\"reference\":\"R-" + uid() + "\",\"name\":\"Ciment test\",\"categoryId\":" + cat
                + ",\"unitId\":" + unitId(unitCode) + ",\"characteristics\":\"C1\"}"));
        long sA = id(create("/api/suppliers", "{\"name\":\"A-" + uid() + "\"}"));
        long sB = id(create("/api/suppliers", "{\"name\":\"B-" + uid() + "\"}"));
        for (String[] o : new String[][]{{p1, "" + sA}, {p2, "" + sB}, {p3, "" + sA}}) observe(mat, o[1], unitId(saleUnitCode), o[0], true);
        return mat;
    }

    private long observe(long mat, String supplier, long saleUnit, String price, boolean validate) throws Exception {
        long obs = id(create("/api/price-observations", "{\"materialId\":" + mat + ",\"supplierId\":" + supplier + ",\"locality\":\"Lomé\",\"saleUnitId\":"
                + saleUnit + ",\"unitPrice\":" + price + ",\"characteristics\":\"C1\",\"sourceReference\":\"devis fournisseur\"}"));
        if (validate) ok("/api/price-observations/" + obs + "/validate", "");
        return obs;
    }

    private static final String MANUAL_LINE = "{\"designation\":\"Main d'œuvre maçon\",\"unit\":\"f\",\"quantity\":1,\"unitPrice\":5000}";

    private String quote(long mat, String extra) throws Exception {
        return create("/api/quotes", "{\"clientName\":\"Koffi Agbodjan\",\"clientAddress\":\"Adidogomé\",\"projectTitle\":\"Mur de clôture\","
                + "\"siteLocation\":\"Lomé\"," + extra + "\"lines\":[{\"materialId\":" + mat + ",\"quantity\":10}," + MANUAL_LINE + "]}");
    }

    // ---------- tests ----------

    @Test
    void quoteWithoutAnyTaxConfigurationHasNoTax() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        String q = quote(mat, "\"discountPercent\":10,");
        assertEquals(0, ((Number) JsonPath.read(q, "$.taxes.length()")).intValue());
        // 50000 + 5000 = 55000 ; remise 5500 ; net 49500 ; aucune taxe
        mvc.perform(get("/api/quotes/" + id(q)))
                .andExpect(jsonPath("$.subtotal").value(55000.00))
                .andExpect(jsonPath("$.discountAmount").value(5500.00))
                .andExpect(jsonPath("$.netHt").value(49500.00))
                .andExpect(jsonPath("$.taxTotal").value(0.00))
                .andExpect(jsonPath("$.totalTtc").value(49500.00))
                .andExpect(jsonPath("$.number").value(startsWith("DEV-")))
                .andExpect(jsonPath("$.lines[0].unitPrice").value(5000.00))
                .andExpect(jsonPath("$.lines[0].priceSource").value("CATALOG"))
                .andExpect(jsonPath("$.lines[0].priceConfidence").value("HIGH"))
                .andExpect(jsonPath("$.lines[0].priceBasis").value(containsString("Mediane de 3")))
                .andExpect(jsonPath("$.lines[1].priceSource").value("MANUAL"))
                .andExpect(jsonPath("$.history.length()").value(1))
                .andExpect(jsonPath("$.company.name").value("NASIM IMMOBILIER"));
    }

    @Test
    void taxesAreExplicitValidatedAndSnapshotted() throws Exception {
        String code = "T10-" + uid();
        create("/api/tax-rates", "{\"code\":\"" + code + "\",\"label\":\"Taxe test\",\"ratePercent\":10}");
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        String body = "{\"clientName\":\"C\",\"projectTitle\":\"P\",\"siteLocation\":\"Lomé\",\"discountPercent\":10,\"transportCost\":1000,"
                + "\"transportTaxCode\":\"" + code + "\",\"lines\":[{\"materialId\":" + mat + ",\"quantity\":10,\"taxCode\":\"" + code + "\"}," + MANUAL_LINE + "]}";
        // taux non valide : refuse
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableContent());
        ok("/api/tax-rates/" + code + "/validate", "");
        String q = create("/api/quotes", body);
        // groupe taxe : 50000 - 5000 + 1000 = 46000 -> taxe 4600 ; groupe non taxe : 5000 - 500 = 4500
        mvc.perform(get("/api/quotes/" + id(q)))
                .andExpect(jsonPath("$.netHt").value(50500.00))
                .andExpect(jsonPath("$.taxes.length()").value(1))
                .andExpect(jsonPath("$.taxes[0].code").value(code))
                .andExpect(jsonPath("$.taxes[0].rate").value(10.00))
                .andExpect(jsonPath("$.taxes[0].base").value(46000.00))
                .andExpect(jsonPath("$.taxTotal").value(4600.00))
                .andExpect(jsonPath("$.totalTtc").value(55100.00))
                .andExpect(jsonPath("$.lines[0].taxRate").value(10.00))
                .andExpect(jsonPath("$.lines[1].taxCode").doesNotExist());
    }

    @Test
    void priceUnavailableForOtherLocalityAndNothingIsPersisted() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        long before = ((Number) JsonPath.read(json("/api/quotes?size=1"), "$.totalElements")).longValue();
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"clientName\":\"C\",\"projectTitle\":\"P\",\"siteLocation\":\"Kara\","
                        + "\"lines\":[" + MANUAL_LINE + ",{\"materialId\":" + mat + ",\"quantity\":1}]}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("PRICE_UNAVAILABLE"));
        assertEquals(before, ((Number) JsonPath.read(json("/api/quotes?size=1"), "$.totalElements")).longValue());
        mvc.perform(get("/api/materials/" + mat + "/price-resolution?locality=Kara"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PRICE_UNAVAILABLE")).andExpect(jsonPath("$.price").doesNotExist());
    }

    @Test
    void observationRulesAndPendingObservationsAreIgnored() throws Exception {
        long cat = id(create("/api/categories", "{\"name\":\"cat-" + uid() + "\"}"));
        long mat = id(create("/api/materials", "{\"reference\":\"R-" + uid() + "\",\"name\":\"M\",\"categoryId\":" + cat + ",\"unitId\":" + unitId("u") + ",\"characteristics\":\"C1\"}"));
        long sup = id(create("/api/suppliers", "{\"name\":\"S-" + uid() + "\"}"));
        // commercial sans fournisseur / date future
        mvc.perform(post("/api/price-observations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialId\":" + mat + ",\"locality\":\"Lomé\",\"unitPrice\":10}")).andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/price-observations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialId\":" + mat + ",\"supplierId\":" + sup + ",\"locality\":\"Lomé\",\"unitPrice\":10,\"observedOn\":\"" + LocalDate.now().plusDays(2) + "\"}"))
                .andExpect(status().isUnprocessableContent());
        // 1 validee + 1 en attente : minimum de 2 non atteint
        observe(mat, "" + sup, unitId("u"), "100", true);
        long pending = observe(mat, "" + sup, unitId("u"), "100", false);
        mvc.perform(get("/api/materials/" + mat + "/price-resolution?locality=Lomé")).andExpect(jsonPath("$.status").value("PRICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.excluded[0].reason").value("NOT_VALIDATED"));
        ok("/api/price-observations/" + pending + "/validate", "");
        mvc.perform(post("/api/price-observations/" + pending + "/reject")).andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/materials/" + mat + "/price-resolution?locality=Lomé")).andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.confidence").value("LOW")); // deux observations, un seul fournisseur
        // l'historique conserve tout
        mvc.perform(get("/api/materials/" + mat + "/prices")).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void administrativeReferencePricesAreNeverMixedWithCommercialOnes() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        long sup = id(create("/api/suppliers", "{\"name\":\"Admin-" + uid() + "\"}"));
        for (int i = 0; i < 2; i++) {
            long o = id(create("/api/price-observations", "{\"materialId\":" + mat + ",\"supplierId\":" + sup + ",\"locality\":\"Lomé\",\"unitPrice\":1000,"
                    + "\"sourceType\":\"ADMINISTRATIVE_REFERENCE\",\"characteristics\":\"C1\"}"));
            ok("/api/price-observations/" + o + "/validate", "");
        }
        mvc.perform(get("/api/materials/" + mat + "/price-resolution?locality=Lomé")).andExpect(jsonPath("$.price").value(5000.00));
        mvc.perform(get("/api/materials/" + mat + "/price-resolution?locality=Lomé&sourceType=ADMINISTRATIVE_REFERENCE")).andExpect(jsonPath("$.price").value(1000.00));
    }

    @Test
    void saleUnitIsConvertedToTheMaterialUnit() throws Exception {
        // materiau en kg, observations en tonne : 1 t = 1000 kg (conversion migree)
        long mat = materialWithPrices("kg", "t", "99000", "100000", "101000");
        mvc.perform(get("/api/materials/" + mat + "/price-resolution?locality=Lomé")).andExpect(jsonPath("$.price").value(100.00));
        // unites sans conversion declaree : observation refusee
        long cat = id(create("/api/categories", "{\"name\":\"cat-" + uid() + "\"}"));
        long m2 = id(create("/api/materials", "{\"reference\":\"R-" + uid() + "\",\"name\":\"M\",\"categoryId\":" + cat + ",\"unitId\":" + unitId("m3") + "}"));
        long sup = id(create("/api/suppliers", "{\"name\":\"S-" + uid() + "\"}"));
        mvc.perform(post("/api/price-observations").contentType(MediaType.APPLICATION_JSON)
                .content("{\"materialId\":" + m2 + ",\"supplierId\":" + sup + ",\"locality\":\"Lomé\",\"saleUnitId\":" + unitId("l") + ",\"unitPrice\":10}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void draftCanBeEditedThenIssuedAndBecomesImmutable() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        String q = quote(mat, "");
        long qid = id(q);
        long line0 = ((Number) JsonPath.read(q, "$.lines[0].id")).longValue();
        long sup = id(create("/api/suppliers", "{\"name\":\"S-" + uid() + "\"}"));

        // les prix changent : l'ancien devis ne bouge pas
        for (int i = 0; i < 3; i++) observe(mat, "" + sup, unitId("sac"), "9999", true);
        mvc.perform(get("/api/quotes/" + qid)).andExpect(jsonPath("$.lines[0].unitPrice").value(5000.00)).andExpect(jsonPath("$.totalTtc").value(55000.00));

        // edition du brouillon : quantite, ajout, suppression, en-tete
        mvc.perform(put("/api/quotes/" + qid + "/lines/" + line0).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":20}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalTtc").value(105000.00));
        mvc.perform(post("/api/quotes/" + qid + "/lines").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"designation\":\"Transport interne\",\"unit\":\"f\",\"quantity\":2,\"unitPrice\":1500}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines.length()").value(3)).andExpect(jsonPath("$.totalTtc").value(108000.00));
        mvc.perform(put("/api/quotes/" + qid).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"Nouveau client\",\"projectTitle\":\"Mur\",\"siteLocation\":\"Lomé\",\"discountPercent\":50}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.clientName").value("Nouveau client")).andExpect(jsonPath("$.totalTtc").value(54000.00))
                .andExpect(jsonPath("$.lines.length()").value(3));
        // nouvelle selection de prix : 3 x 9999 + 3 prix anciens -> mediane 7599,50 ; les lignes manuelles restent
        mvc.perform(post("/api/quotes/" + qid + "/reprice")).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].unitPrice").value(7599.50)).andExpect(jsonPath("$.lines[1].unitPrice").value(5000.00));
        long line1 = ((Number) JsonPath.read(json("/api/quotes/" + qid), "$.lines[1].id")).longValue();
        mvc.perform(delete("/api/quotes/" + qid + "/lines/" + line1)).andExpect(status().isOk())
                .andExpect(jsonPath("$.lines.length()").value(2)).andExpect(jsonPath("$.lines[1].order").value(2));

        // emission
        mvc.perform(post("/api/quotes/" + qid + "/issue")).andExpect(jsonPath("$.status").value("ISSUED"))
                .andExpect(jsonPath("$.history.length()").value(2)).andExpect(jsonPath("$.history[1].from").value("DRAFT"))
                .andExpect(jsonPath("$.history[1].to").value("ISSUED"));
        long first = ((Number) JsonPath.read(json("/api/quotes/" + qid), "$.lines[0].id")).longValue();
        mvc.perform(post("/api/quotes/" + qid + "/issue")).andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/quotes/" + qid + "/lines").contentType(MediaType.APPLICATION_JSON).content(MANUAL_LINE)).andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/quotes/" + qid + "/lines/" + first).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}")).andExpect(status().isUnprocessableContent());
        mvc.perform(delete("/api/quotes/" + qid + "/lines/" + first)).andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/quotes/" + qid + "/reprice")).andExpect(status().isUnprocessableContent());
        mvc.perform(put("/api/quotes/" + qid).contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientName\":\"X\",\"projectTitle\":\"Y\",\"siteLocation\":\"Z\"}")).andExpect(status().isUnprocessableContent());
        mvc.perform(delete("/api/quotes/" + qid)).andExpect(status().isUnprocessableContent());
    }

    @Test
    void oversizedAmountsAreRejectedWith422NotAConflict() throws Exception {
        String line = "{\"designation\":\"X\",\"unit\":\"f\",\"quantity\":999999999999,\"unitPrice\":9999999999999}";
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"K\",\"projectTitle\":\"P\",\"siteLocation\":\"Lomé\",\"lines\":[" + line + "]}"))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void issueWithStaleVersionIsRejectedWith409() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        String q = quote(mat, "");
        long qid = id(q);
        long line0 = ((Number) JsonPath.read(q, "$.lines[0].id")).longValue();
        int seen = ((Number) JsonPath.read(q, "$.version")).intValue();
        // quelqu'un d'autre modifie le brouillon apres notre lecture
        mvc.perform(put("/api/quotes/" + qid + "/lines/" + line0).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":20}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/quotes/" + qid + "/issue?version=" + seen)).andExpect(status().isConflict());
        mvc.perform(get("/api/quotes/" + qid)).andExpect(jsonPath("$.status").value("DRAFT"));
        int current = ((Number) JsonPath.read(json("/api/quotes/" + qid), "$.version")).intValue();
        mvc.perform(post("/api/quotes/" + qid + "/issue?version=" + current)).andExpect(jsonPath("$.status").value("ISSUED"));
    }

    @Test
    void pendingObservationsDoNotCrowdOutValidatedOnesFromThePriceWindow() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        long sup = id(create("/api/suppliers", "{\"name\":\"P-" + uid() + "\"}"));
        for (int i = 0; i < 205; i++) observe(mat, "" + sup, unitId("sac"), "9999", false); // en attente, plus recentes (id plus grand)
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientName\":\"K\",\"projectTitle\":\"P\",\"siteLocation\":\"Lomé\",\"lines\":[{\"materialId\":" + mat + ",\"quantity\":1}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.lines[0].unitPrice").value(5000.00));
    }

    @Test
    void concurrentValidateAndRejectOfTheSameObservationCannotBothSucceed() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        long sup = id(create("/api/suppliers", "{\"name\":\"V-" + uid() + "\"}"));
        long obs = observe(mat, "" + sup, unitId("sac"), "5100", false);
        // deux lectures simultanees de la meme observation en attente
        var first = observationRepository.findById(obs).orElseThrow();
        var second = observationRepository.findById(obs).orElseThrow();
        first.validate();
        observationRepository.saveAndFlush(first);
        second.reject();
        assertThrows(OptimisticLockingFailureException.class, () -> observationRepository.saveAndFlush(second));
        assertEquals("VALIDATED", jdbc.queryForObject("select status from price_observations where id = ?", String.class, obs));
    }

    @Test
    void legacyQuotesGetTheirNetAndTaxTotalsBackfilled() throws Exception {
        // forme d'un devis cree avant V3 : net_ht et tax_total a 0 par defaut, total_ttc = ancien total avec TVA
        jdbc.update("insert into quotes (quote_number, client_name, project_title, status, issue_date, validity_days, discount_percent,"
                + " subtotal, discount_amount, total_ttc, version, created_at) values ('LEGACY-" + uid() + "', 'Ancien', 'Ancien', 'ISSUED', current_date, 30, 10,"
                + " 10000.00, 1000.00, 10620.00, 0, current_timestamp)");
        // un devis recent (net_ht > 0) ne doit jamais etre touche
        jdbc.update("insert into quotes (quote_number, client_name, project_title, status, issue_date, validity_days, discount_percent,"
                + " subtotal, discount_amount, net_ht, tax_total, total_ttc, version, created_at) values ('RECENT-" + uid() + "', 'Recent', 'Recent', 'ISSUED', current_date, 30, 0,"
                + " 5000.00, 0.00, 5000.00, 900.00, 5900.00, 0, current_timestamp)");
        try (var c = jdbc.getDataSource().getConnection()) {
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V6__backfill_legacy_quote_totals.sql"));
        }
        var legacy = jdbc.queryForMap("select net_ht, tax_total from quotes where client_name = 'Ancien' order by id desc limit 1");
        assertEquals(0, new java.math.BigDecimal("9000.00").compareTo((java.math.BigDecimal) legacy.get("net_ht")));
        assertEquals(0, new java.math.BigDecimal("1620.00").compareTo((java.math.BigDecimal) legacy.get("tax_total")));
        var recent = jdbc.queryForMap("select net_ht, tax_total from quotes where client_name = 'Recent' order by id desc limit 1");
        assertEquals(0, new java.math.BigDecimal("5000.00").compareTo((java.math.BigDecimal) recent.get("net_ht")));
        assertEquals(0, new java.math.BigDecimal("900.00").compareTo((java.math.BigDecimal) recent.get("tax_total")));
    }

    @Test
    void templatesArePaginatedAndSoftDeleted() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        String tag = "tpl-" + uid();
        long[] ids = new long[3];
        for (int i = 0; i < 3; i++)
            ids[i] = id(create("/api/quote-templates", "{\"name\":\"" + tag + "-" + i + "\",\"items\":[{\"materialId\":" + mat + ",\"quantity\":2}]}"));
        mvc.perform(get("/api/quote-templates?q=" + tag + "&size=2&sort=name,desc"))
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.content[0].name").value(tag + "-2"));
        mvc.perform(get("/api/quote-templates?sort=bogus")).andExpect(status().isBadRequest());

        mvc.perform(delete("/api/quote-templates/" + ids[0])).andExpect(status().isNoContent());
        mvc.perform(get("/api/quote-templates?q=" + tag)).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(delete("/api/quote-templates/" + ids[0])).andExpect(status().isNotFound());
        // le nom est de nouveau disponible
        create("/api/quote-templates", "{\"name\":\"" + tag + "-0\",\"items\":[{\"materialId\":" + mat + ",\"quantity\":1}]}");
    }

    @Test
    void draftQuoteIsSoftDeleted() throws Exception {
        long qid = id(quote(materialWithPrices("sac", "sac", "4800", "5000", "5200"), ""));
        mvc.perform(delete("/api/quotes/" + qid)).andExpect(status().isNoContent());
        mvc.perform(get("/api/quotes/" + qid)).andExpect(status().isNotFound());
        mvc.perform(get("/api/quotes/" + qid + "/pdf")).andExpect(status().isNotFound());
    }

    @Test
    void emptyDraftCannotBeIssued() throws Exception {
        String q = quote(materialWithPrices("sac", "sac", "4800", "5000", "5200"), "");
        long qid = id(q);
        for (int i = 0; i < 2; i++) {
            long l = ((Number) JsonPath.read(json("/api/quotes/" + qid), "$.lines[0].id")).longValue();
            mvc.perform(delete("/api/quotes/" + qid + "/lines/" + l)).andExpect(status().isOk());
        }
        mvc.perform(post("/api/quotes/" + qid + "/issue")).andExpect(status().isUnprocessableContent());
    }

    @Test
    void discountAboveMaximumIsRejectedAndValidationIsReported() throws Exception {
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"lines\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientName\":\"C\",\"projectTitle\":\"P\",\"siteLocation\":\"L\",\"discountPercent\":101,\"lines\":[" + MANUAL_LINE + "]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/quotes/999999")).andExpect(status().isNotFound());
    }

    @Test
    void listsArePaginatedSortedAndFilteredUniformly() throws Exception {
        String tag = uid();
        for (String n : new String[]{"b", "a", "c"}) create("/api/categories", "{\"name\":\"pg-" + tag + n + "\"}");
        mvc.perform(get("/api/categories?q=pg-" + tag + "&size=2&sort=name,desc"))
                .andExpect(jsonPath("$.content.length()").value(2)).andExpect(jsonPath("$.content[0].name").value("pg-" + tag + "c"))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2));
        mvc.perform(get("/api/categories?q=pg-" + tag + "&size=2&page=1&sort=name,desc")).andExpect(jsonPath("$.content.length()").value(1));
        mvc.perform(get("/api/categories?sort=password,asc")).andExpect(status().isBadRequest());
        for (String url : new String[]{"/api/materials", "/api/suppliers", "/api/units", "/api/price-observations", "/api/quotes", "/api/tax-rates", "/api/unit-conversions"})
            mvc.perform(get(url + "?size=1")).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.totalPages").exists());
        long cat = id(create("/api/categories", "{\"name\":\"cat-" + uid() + "\"}"));
        create("/api/materials", "{\"reference\":\"ZZ-" + tag + "\",\"name\":\"Zébulon\",\"categoryId\":" + cat + ",\"unitId\":" + unitId("u") + "}");
        mvc.perform(get("/api/materials?categoryId=" + cat + "&q=zz-" + tag)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].unitCode").value("u"));
        mvc.perform(get("/api/materials?categoryId=" + cat + "&active=false")).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void pdfAndDocxAreBuiltFromTheSnapshot() throws Exception {
        long mat = materialWithPrices("sac", "sac", "4800", "5000", "5200");
        StringBuilder lines = new StringBuilder(MANUAL_LINE);
        for (int i = 0; i < 60; i++)
            lines.append(",{\"designation\":\"Fourniture n°").append(i).append(" très longue désignation avec accents éàùçœ pour vérifier le retour à la ligne automatique dans la cellule du tableau\",\"unit\":\"u\",\"quantity\":1,\"unitPrice\":1000}");
        String q = create("/api/quotes", "{\"clientName\":\"Société Générale du Bâtiment\",\"clientAddress\":\"Rue de l'Église\",\"projectTitle\":\"Villa R+1\","
                + "\"siteLocation\":\"Lomé - Agoè\",\"transportCost\":2500,\"lines\":[" + lines + ",{\"materialId\":" + mat + ",\"quantity\":3,\"unitPrice\":5000}]}");
        long qid = id(q);
        // le changement de configuration/catalogue ne change pas le contenu : on compare deux exports successifs
        byte[] pdf = mvc.perform(get("/api/quotes/" + qid + "/pdf")).andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue(doc.getNumberOfPages() >= 2, "tableau multipage attendu");
            String text = new PDFTextStripper().getText(doc);
            for (String expected : new String[]{"NASIM IMMOBILIER", "Désignation", "Société Générale du Bâtiment", "Chantier", "Lomé - Agoè",
                    "Frais de transport", "TOTAL GÉNÉRAL TTC", "Aucune taxe appliquée", "Cachet et signature", "Valable jusqu", "éàùçœ", "Page 1 / "})
                assertTrue(text.contains(expected), "PDF sans : " + expected);
            assertTrue(text.contains("DEV-"), "numero de devis");
        }
        byte[] docx = mvc.perform(get("/api/quotes/" + qid + "/docx")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(docx))) {
            var items = doc.getTables().get(1); // 0 = en-tete societe, 1 = lignes
            assertEquals(1 + 62, items.getNumberOfRows());
            assertTrue(items.getRow(0).getTableCells().get(1).getText().equals("Désignation"));
            String all = doc.getParagraphs().stream().map(p -> p.getText()).reduce("", String::concat);
            assertTrue(all.contains("Chantier : Lomé - Agoè") && all.contains("TOTAL GÉNÉRAL TTC") && all.contains("Frais de transport"));
        }
        mvc.perform(get("/api/quotes/999999/pdf")).andExpect(status().isNotFound());
    }

    @Test
    void swaggerIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andExpect(content().string(containsString("/api/quotes/{id}/lines")));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
