package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masmatix.suite.generated.bakery.BakeryItem;
import com.masmatix.suite.generated.bakery.BakeryItemRepository;
import com.masmatix.suite.generated.bakery.ProductionBatch;
import com.masmatix.suite.generated.bakery.ProductionBatchRepository;
import com.masmatix.suite.generated.garment.GarmentItem;
import com.masmatix.suite.generated.garment.GarmentItemRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** End-to-end API tests for the garment shop, IMEI tracker and bakery. Each test makes its own rows. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShopSystemsApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;
    @Autowired GarmentItemRepository garments;
    @Autowired BakeryItemRepository bakeryItems;
    @Autowired ProductionBatchRepository batches;

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private ResultActions postJson(String url, String user, Object body) throws Exception {
        return mvc.perform(post(url).header("Authorization", token(user))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }

    private JsonNode read(ResultActions r) throws Exception {
        return json.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    // ---------------------------------------------------------------- garment

    private GarmentItem garment(String code, String price, int stock) {
        GarmentItem g = new GarmentItem();
        g.setStyleCode(code);
        g.setName("Test kurta (sample)");
        g.setSizeLabel("M");
        g.setColour("Green");
        g.setPrice(new BigDecimal(price));
        g.setStock(stock);
        return garments.save(g);
    }

    @Test
    void garmentSaleTakesStockAndPrintsReceipt() throws Exception {
        garment("T-SALE-M", "24.00", 2);
        JsonNode sale = read(postJson("/api/garment/sales", "staff", Map.of(
                "items", List.of(Map.of("styleCode", "t-sale-m", "qty", 2)),
                "paymentMethod", "CASH", "amountTendered", 50))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.total").value(48.0))
                .andExpect(jsonPath("$.changeDue").value(2.0)));
        assertThat(sale.get("receiptNumber").asText()).startsWith("G");
        assertThat(garments.findAll().stream().filter(g -> g.getStyleCode().equals("T-SALE-M")).findFirst().orElseThrow().getStock()).isZero();

        postJson("/api/garment/sales", "staff", Map.of("items", List.of(Map.of("styleCode", "T-SALE-M", "qty", 1)),
                "paymentMethod", "CARD")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/garment/sales/" + sale.get("id").asLong() + "/receipt.pdf").header("Authorization", token("staff")))
                .andExpect(status().isOk()).andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void exchangeSwapsStockOnceAndRecordsTheDifference() throws Exception {
        garment("T-EX-M", "24.00", 1);
        garment("T-EX-L", "26.50", 1);
        String receipt = read(postJson("/api/garment/sales", "staff", Map.of(
                "items", List.of(Map.of("styleCode", "T-EX-M", "qty", 1)), "paymentMethod", "CARD"))
                .andExpect(status().isCreated())).get("receiptNumber").asText();

        Map<String, Object> swap = Map.of("receiptNumber", receipt, "returnedStyleCode", "T-EX-M",
                "newStyleCode", "T-EX-L", "reason", "SIZE");
        postJson("/api/garment/exchange", "staff", swap).andExpect(status().isCreated())
                .andExpect(jsonPath("$.difference").value(2.5))
                .andExpect(jsonPath("$.returnedToStock").value(true))
                .andExpect(jsonPath("$.exchange.refundGiven").value(false));
        // The same item cannot be exchanged twice from one receipt.
        postJson("/api/garment/exchange", "staff", swap).andExpect(status().isUnprocessableEntity());
        assertThat(garments.findAll().stream().filter(g -> g.getStyleCode().startsWith("T-EX-"))
                .mapToInt(GarmentItem::getStock).boxed().toList()).containsExactlyInAnyOrder(1, 0);
    }

    // ---------------------------------------------------------------- IMEI

    @Test
    void imeiLookupShowsCheckDigitAndWarranty() throws Exception {
        String soldOn = today().minusMonths(2).toString();
        postJson("/api/imei/handsets", "manager", Map.of("imei", "490154203237518", "brand", "SampleBrand",
                "model", "T1 (sample)", "soldOn", soldOn, "warrantyMonths", 12)).andExpect(status().isCreated());

        mvc.perform(get("/api/imei/lookup/490154203237518").header("Authorization", token("staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkDigitValid").value(true))
                .andExpect(jsonPath("$.inWarranty").value(true))
                .andExpect(jsonPath("$.handset.model").value("T1 (sample)"));
        mvc.perform(get("/api/imei/lookup/490154203237517").header("Authorization", token("staff")))
                .andExpect(jsonPath("$.checkDigitValid").value(false))
                .andExpect(jsonPath("$.handset").doesNotExist());
    }

    @Test
    void handsetWithWrongCheckDigitIsNotSaved() throws Exception {
        postJson("/api/imei/handsets", "manager", Map.of("imei", "490154203237519", "brand", "SampleBrand",
                "model", "Typo (sample)")).andExpect(status().isBadRequest());
    }

    @Test
    void claimOnlyInsideWarrantyAndOneOpenAtATime() throws Exception {
        postJson("/api/imei/handsets", "manager", Map.of("imei", "350000000000006", "brand", "SampleBrand",
                "model", "T2 (sample)", "soldOn", today().minusDays(10).toString(), "warrantyMonths", 6))
                .andExpect(status().isCreated());
        postJson("/api/imei/claims/open", "staff", Map.of("imei", "35000000 0000006", "fault", "Screen flicker (sample)"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("RECEIVED"));
        postJson("/api/imei/claims/open", "staff", Map.of("imei", "350000000000006", "fault", "Again (sample)"))
                .andExpect(status().isUnprocessableEntity());

        // Seeded handset sold in June 2025 with 12 months: cover has ended.
        postJson("/api/imei/claims/open", "staff", Map.of("imei", "000000000000034", "fault", "Battery (sample)"))
                .andExpect(status().isUnprocessableEntity());
        postJson("/api/imei/claims/open", "staff", Map.of("imei", "490154203237501", "fault", "x"))
                .andExpect(status().isUnprocessableEntity());
    }

    // ---------------------------------------------------------------- bakery

    private BakeryItem bakeryItem(String name, int shelfLife) {
        BakeryItem b = new BakeryItem();
        b.setName(name);
        b.setPrice(new BigDecimal("1.50"));
        b.setShelfLifeDays(shelfLife);
        b.setCategory("BREAD");
        return bakeryItems.save(b);
    }

    private ProductionBatch batch(String code, String item, int qty, LocalDate expires) {
        ProductionBatch b = new ProductionBatch();
        b.setBatchCode(code);
        b.setItemName(item);
        b.setQuantity(qty);
        b.setBakedOn(expires.minusDays(3));
        b.setExpiresOn(expires);
        return batches.save(b);
    }

    @Test
    void bakeSetsExpiryFromShelfLife() throws Exception {
        BakeryItem it = bakeryItem("Test naan (sample)", 2);
        postJson("/api/bakery/bake", "staff", Map.of("itemId", it.getId(), "quantity", 30))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresOn").value(today().plusDays(2).toString()))
                .andExpect(jsonPath("$.batchCode").value(org.hamcrest.Matchers.startsWith("B-")));
    }

    @Test
    void counterSaleUsesOldestFreshBatchAndNeverSellsExpired() throws Exception {
        BakeryItem it = bakeryItem("Test rusk (sample)", 5);
        batch("T-RUSK-OLD", it.getName(), 10, today().minusDays(1));
        ProductionBatch todays = batch("T-RUSK-TODAY", it.getName(), 3, today());
        batch("T-RUSK-NEW", it.getName(), 20, today().plusDays(3));

        postJson("/api/bakery/sell", "staff", Map.of("itemId", it.getId(), "quantity", 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(7.5))
                .andExpect(jsonPath("$.fromBatches[0].batchCode").value("T-RUSK-TODAY"))
                .andExpect(jsonPath("$.fromBatches[0].quantity").value(3))
                .andExpect(jsonPath("$.fromBatches[1].quantity").value(2));
        assertThat(batches.findById(todays.getId()).orElseThrow().getQuantity()).isZero();
        // 18 fresh left; the 10 expired ones are never sold.
        postJson("/api/bakery/sell", "staff", Map.of("itemId", it.getId(), "quantity", 19))
                .andExpect(status().isUnprocessableEntity());

        JsonNode expiry = read(mvc.perform(get("/api/bakery/expiry").header("Authorization", token("staff"))));
        assertThat(expiry.findValues("batchCode").stream().map(JsonNode::asText).toList()).contains("T-RUSK-OLD");
    }

    @Test
    void onlyManagersWriteOffBatches() throws Exception {
        ProductionBatch b = batch("T-WO-1", "Test bun (sample)", 4, today().minusDays(1));
        mvc.perform(post("/api/bakery/batches/" + b.getId() + "/write-off").header("Authorization", token("staff")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/bakery/batches/" + b.getId() + "/write-off").header("Authorization", token("manager")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.quantity").value(0));
    }
}
