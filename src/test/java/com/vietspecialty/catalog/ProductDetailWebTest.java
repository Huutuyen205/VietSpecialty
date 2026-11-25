package com.vietspecialty.catalog;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.repository.ProductCommentRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
public class ProductDetailWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCommentRepository productCommentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
    @DisplayName("DEF-24: GET /products/{slug} không tồn tại hoặc HIDDEN trả về 404 view error/404")
    void testProductDetailNotFound() throws Exception {
        mockMvc.perform(get("/products/non-existent-slug-999"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("errorCode", "PROD_001"));

        Product activeProduct = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .findFirst().orElseThrow();
        activeProduct.setStatus(ProductStatus.HIDDEN);
        productRepository.saveAndFlush(activeProduct);

        mockMvc.perform(get("/products/" + activeProduct.getSlug()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/404"))
                .andExpect(model().attribute("errorCode", "PROD_001"));
    }

    @Test
    @DisplayName("DEF-02: Form POST application/x-www-form-urlencoded và JSON POST tới /products/{id}/comments hoạt động đúng")
    void testPostProductCommentHtmlFormAndJson() throws Exception {
        Product product = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .findFirst().orElseThrow();

        User buyer = userRepository.save(User.builder()
                .fullName("Comment Buyer")
                .email("cmt_" + UUID.randomUUID().toString().substring(0, 8) + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0912345678")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        // 1. Valid HTML form submission (application/x-www-form-urlencoded) -> 302 redirect + flash
        String formQuestion = "Sản phẩm này đóng gói hút chân không được mấy tháng ạ?";
        mockMvc.perform(post("/products/" + product.getId() + "/comments")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("content", formQuestion))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/" + product.getSlug() + "#qa-section"))
                .andExpect(flash().attributeExists("commentSuccess", "successMessage"));

        assertTrue(productCommentRepository.findAll().stream()
                .anyMatch(c -> formQuestion.equals(c.getContent())));

        // 2. Blank content HTML form -> 302 redirect + flash error
        mockMvc.perform(post("/products/" + product.getId() + "/comments")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("content", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/" + product.getSlug() + "#qa-section"))
                .andExpect(flash().attributeExists("commentError", "errorMessage"));

        // 3. Too long content (>1000 chars) HTML form -> 302 redirect + flash error
        mockMvc.perform(post("/products/" + product.getId() + "/comments")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("content", "B".repeat(1005)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products/" + product.getSlug() + "#qa-section"))
                .andExpect(flash().attributeExists("commentError", "errorMessage"));

        // 4. JSON body POST -> 200 JSON
        mockMvc.perform(post("/products/" + product.getId() + "/comments")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hỏi qua JSON body có hỗ trợ không?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 5. Unauthenticated HTML form POST -> 302 /login
        mockMvc.perform(post("/products/" + product.getId() + "/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("content", "Khách vãng lai hỏi"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
