package com.vietspecialty.catalog;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class StaticPlaceholderResourcesTest {

    @Autowired
    private MockMvc mockMvc;

    private static final byte[] PNG_MAGIC = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final Pattern X_FOR_TEMPLATE_PATTERN = Pattern.compile(
            "<template[^>]*x-for[^>]*>(.*?)</template>",
            Pattern.DOTALL
    );

    @Test
    @DisplayName("DEF-23: GET 5 ảnh placeholder trả 200 OK với đúng content-type và PNG magic bytes thực")
    void testAllPlaceholderImagesServeSuccessfullyWithValidFormats() throws Exception {
        for (String svgPath : new String[]{"/images/demo/placeholder.svg", "/images/demo/shop-placeholder.svg"}) {
            String svgBody = mockMvc.perform(get(svgPath))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/svg+xml"))
                    .andReturn()
                    .getResponse()
                    .getContentAsString(StandardCharsets.UTF_8);

            assertTrue(svgBody.contains("<svg"), svgPath + " phải chứa thẻ <svg");
            assertTrue(svgBody.contains("</svg>"), svgPath + " phải đóng thẻ </svg>");
        }

        for (String pngPath : new String[]{"/images/placeholder.png", "/images/default-product.png", "/images/default-shop.png"}) {
            byte[] bytes = mockMvc.perform(get(pngPath))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("image/png"))
                    .andReturn()
                    .getResponse()
                    .getContentAsByteArray();

            assertTrue(bytes.length > 64, pngPath + " không được rỗng");
            for (int i = 0; i < PNG_MAGIC.length; i++) {
                assertEquals(PNG_MAGIC[i], bytes[i], pngPath + " sai PNG magic byte tại vị trí " + i);
            }
        }
    }

    @Test
    @DisplayName("DEF-14 & DEF-19: Kiểm tra template checkout/order-success vs_checkout_form_state, x-for không chứa data-lucide và 4 layouts có MutationObserver")
    void testCheckoutPersistenceAndLucideTemplatesInvariants() throws Exception {
        String checkoutHtml = new String(
                new ClassPathResource("templates/web/checkout.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
        assertTrue(checkoutHtml.contains("@submit=\"submitting = true\""));
        assertTrue(checkoutHtml.contains("vs_checkout_form_state"));
        assertTrue(checkoutHtml.contains("saveCheckoutFormState()"));
        assertTrue(checkoutHtml.contains("restoreCheckoutFormState("));

        String orderSuccessHtml = new String(
                new ClassPathResource("templates/web/order-success.html").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8
        );
        assertTrue(orderSuccessHtml.contains("sessionStorage.removeItem('vs_checkout_gifts')"));
        assertTrue(orderSuccessHtml.contains("sessionStorage.removeItem('vs_checkout_form_state')"));

        for (String templatePath : new String[]{
                "templates/web/product-detail.html",
                "templates/web/orders/detail.html",
                "templates/seller/reviews/index.html"
        }) {
            String html = new String(
                    new ClassPathResource(templatePath).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            Matcher matcher = X_FOR_TEMPLATE_PATTERN.matcher(html);
            while (matcher.find()) {
                String block = matcher.group(1);
                assertFalse(block.contains("data-lucide"),
                        templatePath + " không được chứa data-lucide bên trong <template x-for>: " + block);
            }
        }

        for (String layoutPath : new String[]{
                "templates/layouts/base.html",
                "templates/layout/base.html",
                "templates/layouts/seller-base.html",
                "templates/layouts/admin-base.html"
        }) {
            String html = new String(
                    new ClassPathResource(layoutPath).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8
            );
            assertTrue(html.contains("MutationObserver"), layoutPath + " phải có MutationObserver cho Lucide");
            assertTrue(html.contains("lucide.createIcons()"), layoutPath + " phải gọi lucide.createIcons()");
            assertTrue(html.contains("[data-lucide]:not(svg)"), layoutPath + " phải lọc [data-lucide]:not(svg) tránh vòng lặp");
        }
    }
}
