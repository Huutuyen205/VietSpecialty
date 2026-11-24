package com.vietspecialty.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.controller.api.CartApiController;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CartService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class PublicCatalogAndCartApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private User createFreshBuyer(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .fullName("Buyer " + prefix)
                .email(prefix + "_" + uid + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0912345678")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("GET /api/provinces trả về 34 tỉnh/thành và lọc chính xác theo regionId")
    void testGetProvincesEndpoint() throws Exception {
        mockMvc.perform(get("/api/provinces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(34)));

        mockMvc.perform(get("/api/provinces").param("regionId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(15)));

        mockMvc.perform(get("/api/provinces").param("regionId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)));

        mockMvc.perform(get("/api/provinces").param("regionId", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("GET /sitemap.xml và /robots.txt trả về định dạng SEO chuẩn")
    void testSitemapAndRobots() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(allOf(
                        containsString("<urlset"),
                        containsString("/categories/"),
                        containsString("/products/")
                )));

        mockMvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string(allOf(
                        containsString("User-agent: *"),
                        containsString("Disallow: /admin/"),
                        containsString("Sitemap:")
                )));
    }

    @Test
    @DisplayName("ShopController GET /shops/{slug}: approved (200), non-existent (400 SHOP_001), suspended (400 SHOP_004)")
    void testShopDetailAndStatusHandling() throws Exception {
        // Approved seeded shop
        mockMvc.perform(get("/shops/htx-tra-tan-cuong-thai-nguyen"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/shop-detail"))
                .andExpect(model().attributeExists("shop", "productPage", "filter", "categories"));

        // Non-existent shop -> 400 error/500 SHOP_001
        mockMvc.perform(get("/shops/non-existent-shop-999"))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "SHOP_001"));

        // Suspended shop -> 400 error/500 SHOP_004
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User suspendedOwner = userRepository.save(User.builder()
                .fullName("Suspended Owner")
                .email("suspended_owner_" + uid + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build());
        Province province = provinceRepository.findAll().get(0);
        Shop suspendedShop = shopRepository.save(Shop.builder()
                .owner(suspendedOwner)
                .name("Shop Tạm Dừng " + uid)
                .slug("shop-tam-dung-" + uid)
                .province(province)
                .status(ShopStatus.SUSPENDED)
                .build());

        mockMvc.perform(get("/shops/" + suspendedShop.getSlug()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "SHOP_004"));
    }

    @Test
    @DisplayName("CartApiController: GET/PUT/DELETE/count và chống IDOR chéo người dùng")
    void testCartApiCrudCountAndIdor() throws Exception {
        User buyerA = createFreshBuyer("cart_a");
        User buyerB = createFreshBuyer("cart_b");
        CustomUserDetails detailsA = new CustomUserDetails(buyerA);
        CustomUserDetails detailsB = new CustomUserDetails(buyerB);

        Product product = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE && p.getStock() >= 10)
                .findFirst()
                .orElseThrow();

        // Add item via POST /api/cart/items
        mockMvc.perform(post("/api/cart/items")
                        .with(user(detailsA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(product.getId(), 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItemCount").value(2));

        // GET /api/cart
        mockMvc.perform(get("/api/cart").with(user(detailsA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.shopGroups", hasSize(1)))
                .andExpect(jsonPath("$.data.totalItemCount").value(2));

        CartItem itemA = cartItemRepository.findByUserIdOrderByCreatedAtDesc(buyerA.getId()).get(0);

        // PUT /api/cart/items/{id} update quantity to 3
        mockMvc.perform(put("/api/cart/items/" + itemA.getId())
                        .with(user(detailsA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItemCount").value(3));

        // GET /api/cart/count: authenticated USER with 1 distinct row of qty 3 -> 1
        mockMvc.perform(get("/api/cart/count").with(user(detailsA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(1));

        // GET /api/cart/count: guest HTTP -> 401 JSON (per SecurityConfig)
        mockMvc.perform(get("/api/cart/count"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // Direct unit test of CartApiController.getCartCount(null) -> 200 with 0
        ResponseEntity<ApiResponse<Integer>> guestUnitResp = new CartApiController(cartService).getCartCount(null);
        assertEquals(HttpStatus.OK, guestUnitResp.getStatusCode());
        assertNotNull(guestUnitResp.getBody());
        assertEquals(0, guestUnitResp.getBody().getData());

        // Cross-user IDOR: User B tries PUT & DELETE on User A's cart item -> 404 JSON, User A's item intact
        mockMvc.perform(put("/api/cart/items/" + itemA.getId())
                        .with(user(detailsB))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":1}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(delete("/api/cart/items/" + itemA.getId())
                        .with(user(detailsB))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        assertTrue(cartItemRepository.findByIdAndUserId(itemA.getId(), buyerA.getId()).isPresent(),
                "Cart item của nạn nhân không được bị sửa/xóa bởi User B");

        // PUT quantity <= 0 auto-removes item
        mockMvc.perform(put("/api/cart/items/" + itemA.getId())
                        .with(user(detailsA))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItemCount").value(0))
                .andExpect(jsonPath("$.data.shopGroups", hasSize(0)));

        assertEquals(0, cartItemRepository.countByUserId(buyerA.getId()));

        // Re-add and test DELETE /api/cart/items/{id}
        cartService.addToCart(buyerA.getId(), new CartItemRequest(product.getId(), 1));
        CartItem newItemA = cartItemRepository.findByUserIdOrderByCreatedAtDesc(buyerA.getId()).get(0);

        mockMvc.perform(delete("/api/cart/items/" + newItemA.getId())
                        .with(user(detailsA))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalItemCount").value(0));

        assertEquals(0, cartItemRepository.countByUserId(buyerA.getId()));
    }
}
