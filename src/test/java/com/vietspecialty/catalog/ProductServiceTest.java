package com.vietspecialty.catalog;

import com.vietspecialty.dto.request.ProductFilterRequest;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Test
    @DisplayName("Tìm kiếm sản phẩm với filter mặc định không bị lỗi")
    void testSearchProductsDefault() {
        ProductFilterRequest req = new ProductFilterRequest(
                null, null, null, null, null, null, null, null, null, null, null, null, "newest", 0, 16
        );

        Page<ProductCardResponse> page = productService.searchProducts(req);
        assertNotNull(page);
    }

    @Test
    @DisplayName("Tìm kiếm theo từ khóa và lọc OCOP không ném ngoại lệ SQL")
    void testSearchWithKeywordAndOcop() {
        ProductFilterRequest req = new ProductFilterRequest(
                "Trà Tân Cương", "mien-bac", null, null, null, null, null,
                new BigDecimal("50000"), new BigDecimal("500000"),
                true, true, null, "best_seller", 0, 10
        );

        Page<ProductCardResponse> page = productService.searchProducts(req);
        assertNotNull(page);
    }
}

