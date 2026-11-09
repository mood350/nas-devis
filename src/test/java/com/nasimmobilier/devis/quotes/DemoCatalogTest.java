package com.nasimmobilier.devis.quotes;

import com.jayway.jsonpath.JsonPath;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Profil demo : catalogue fictif utilisable pour des brouillons, jamais pour un devis emis. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:demo;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
class DemoCatalogTest {

    @Autowired MockMvc mvc;

    private String body(String url) throws Exception {
        return mvc.perform(get(url)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void demoCatalogCoversTheRequestedMaterials() throws Exception {
        String all = body("/api/materials?size=100&sort=reference");
        assertEquals(16, ((Number) JsonPath.read(all, "$.totalElements")).intValue());
        List<String> refs = JsonPath.read(all, "$.content[*].reference");
        for (String must : List.of("CIM-CPJ425-50", "FER-HA8", "FER-HA10", "FER-HA12", "FER-HA14", "FER-HA16", "SAB-LAG", "GRA-1525",
                "PARP-10", "PARP-15", "PARP-20", "BOIS-COF", "PVC-EVAC-50", "CAB-ELEC-15"))
            assertTrue(refs.contains(must), must);
        String obs = body("/api/price-observations?size=100");
        assertEquals(48, ((Number) JsonPath.read(obs, "$.totalElements")).intValue());
        assertEquals(48, ((List<?>) JsonPath.read(obs, "$.content[?(@.demo==true && @.status=='VALIDATED')]")).size());
    }

    @Test
    void demoPricesProduceDraftsFlaggedDemoThatCannotBeIssued() throws Exception {
        long mat = ((Number) JsonPath.<List<Object>>read(body("/api/materials?q=CIM-CPJ425-50"), "$.content[*].id").get(0)).longValue();
        mvc.perform(get("/api/materials/" + mat + "/price-resolution?locality=Lomé")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK")).andExpect(jsonPath("$.demo").value(true));
        String q = mvc.perform(post("/api/quotes").contentType(MediaType.APPLICATION_JSON).content("{\"clientName\":\"Test\",\"projectTitle\":\"Démo\","
                        + "\"siteLocation\":\"Lomé\",\"lines\":[{\"materialId\":" + mat + ",\"quantity\":10}]}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.containsDemoPrices").value(true))
                .andExpect(jsonPath("$.lines[0].demoPrice").value(true)).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(q, "$.id")).longValue();
        mvc.perform(post("/api/quotes/" + id + "/issue")).andExpect(status().isUnprocessableContent());
        byte[] pdf = mvc.perform(get("/api/quotes/" + id + "/pdf")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (PDDocument doc = PDDocument.load(pdf)) {
            assertTrue(new PDFTextStripper().getText(doc).contains("DOCUMENT DE DÉMONSTRATION"));
        }
    }
}
