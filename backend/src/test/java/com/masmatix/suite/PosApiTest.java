package com.masmatix.suite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.masmatix.suite.pos.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PosApiTest {

    // Sample barcodes from seed/pos-products.json
    private static final String COLA = "2000000000084";      // 0.90, 20% VAT, 60 in stock
    private static final String MILK = "2000000000015";      // 1.45, zero-rated, 40 in stock
    private static final String BATTERIES = "2000000000121"; // 2 in stock

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ProductRepository products;

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + user + "\",\"password\":\"test-password\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    @Test
    void wrongPasswordIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiNeedsAToken() throws Exception {
        mvc.perform(get("/api/pos/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void barcodeLookupFindsSampleProduct() throws Exception {
        mvc.perform(get("/api/pos/products/barcode/" + COLA).header("Authorization", token("staff")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unitPrice").value(0.90));
    }

    @Test
    void cashSaleUpdatesStockTotalsAndGivesReceipt() throws Exception {
        int milkBefore = products.findByBarcode(MILK).orElseThrow().getStockQty();
        String req = "{\"paymentMethod\":\"CASH\",\"amountTendered\":10.00,\"items\":["
                + "{\"barcode\":\"" + MILK + "\",\"qty\":1},"
                + "{\"barcode\":\"" + COLA + "\",\"qty\":3},"
                + "{\"barcode\":\"" + MILK + "\",\"qty\":1}]}";
        String body = mvc.perform(post("/api/pos/sales").header("Authorization", token("staff"))
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode sale = json.readTree(body);

        // milk 2 x 1.45 = 2.90 (0% VAT); cola 3 x 0.90 = 2.70 + 0.54 VAT
        assertThat(sale.get("lines")).hasSize(2);
        assertThat(sale.get("subtotal").decimalValue()).isEqualByComparingTo("5.60");
        assertThat(sale.get("tax").decimalValue()).isEqualByComparingTo("0.54");
        assertThat(sale.get("total").decimalValue()).isEqualByComparingTo("6.14");
        assertThat(sale.get("changeDue").decimalValue()).isEqualByComparingTo("3.86");
        assertThat(sale.get("saleNumber").asText()).startsWith("S");
        assertThat(products.findByBarcode(MILK).orElseThrow().getStockQty()).isEqualTo(milkBefore - 2);

        byte[] pdf = mvc.perform(get("/api/pos/sales/" + sale.get("id").asLong() + "/receipt.pdf")
                        .header("Authorization", token("staff")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void cannotSellMoreThanInStock() throws Exception {
        String req = "{\"paymentMethod\":\"CARD\",\"items\":[{\"barcode\":\"" + BATTERIES + "\",\"qty\":99}]}";
        mvc.perform(post("/api/pos/sales").header("Authorization", token("staff"))
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void notEnoughCashIsRejected() throws Exception {
        String req = "{\"paymentMethod\":\"CASH\",\"amountTendered\":0.10,\"items\":[{\"barcode\":\"" + COLA + "\",\"qty\":1}]}";
        mvc.perform(post("/api/pos/sales").header("Authorization", token("staff"))
                        .contentType(MediaType.APPLICATION_JSON).content(req))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void staffCannotEditCatalogueButManagerCan() throws Exception {
        String product = "{\"sku\":\"T-1\",\"barcode\":\"2000000009995\",\"name\":\"Test item\","
                + "\"unitPrice\":1.00,\"taxRate\":20.00,\"stockQty\":5,\"reorderLevel\":1}";
        mvc.perform(post("/api/pos/products").header("Authorization", token("staff"))
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/pos/products").header("Authorization", token("manager"))
                        .contentType(MediaType.APPLICATION_JSON).content(product))
                .andExpect(status().isCreated());
    }

    @Test
    void generatedSupplierModuleServesSamplesAndCsv() throws Exception {
        mvc.perform(get("/api/pos/suppliers").header("Authorization", token("manager")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2));
        String csv = mvc.perform(get("/api/pos/suppliers/export.csv").header("Authorization", token("manager")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv.lines().findFirst().orElse("")).startsWith("id,");
        assertThat(csv).contains("Northfield Wholesale (sample)");
    }

    @Test
    void invalidEmailInGeneratedModuleIsRejected() throws Exception {
        mvc.perform(post("/api/pos/customers").header("Authorization", token("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"X\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void auditLogIsAdminOnly() throws Exception {
        mvc.perform(get("/api/audit").header("Authorization", token("staff"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/audit").header("Authorization", token("admin"))).andExpect(status().isOk());
    }
}
