package com.vietspecialty.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class ProductDetailWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /products/keo-dua-sau-rieng-ben-tre hiển thị chi tiết sản phẩm đầy đủ")
    void testProductDetailPage() throws Exception {
        mockMvc.perform(get("/products/keo-dua-sau-rieng-ben-tre"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/product-detail"))
                .andExpect(model().attributeExists("product", "relatedProducts", "comments", "commentCount"))
                .andExpect(content().string(containsString("Kẹo Dừa Sầu Riêng Bến Tre")));
    }

    @Test
    @DisplayName("GET /products/non-existent-slug-999 trả về 400 view error/500 với mã PROD_001")
    void testProductDetailNotFound() throws Exception {
        mockMvc.perform(get("/products/non-existent-slug-999"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "PROD_001"));
    }
}
