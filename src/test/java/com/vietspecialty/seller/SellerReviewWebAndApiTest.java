package com.vietspecialty.seller;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import jakarta.persistence.EntityManager;
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

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SellerReviewWebAndApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderGroupRepository orderGroupRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User createUser(String prefix, Role role) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .fullName("User " + prefix)
                .email(prefix + "_" + uid + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0912345678")
                .role(role)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Shop createApprovedShop(User owner, String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Province province = provinceRepository.findAll().get(0);
        return shopRepository.save(Shop.builder()
                .name("Shop " + prefix + " " + uid)
                .slug("shop-" + prefix + "-" + uid)
                .owner(owner)
                .province(province)
                .addressDetail("789 Đường Làng Nghề")
                .phone("0912345678")
                .status(ShopStatus.APPROVED)
                .build());
    }

    private Product createProduct(Shop shop) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0);
        return productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name("Cốm Làng Vòng " + uid)
                .slug("com-" + uid)
                .description("Đặc sản cốm tươi Hà Nội")
                .price(new BigDecimal("180000"))
                .stock(50)
                .unit("Gói 500g")
                .originDetail("Dịch Vọng Hậu, Hà Nội")
                .status(ProductStatus.ACTIVE)
                .build());
    }

    private Review createReviewForProduct(User buyer, Shop shop, Product product, int rating, ReviewStatus status, String content) {
        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OrderGroup group = orderGroupRepository.save(OrderGroup.builder()
                .code("VSG-REV-" + uid)
                .user(buyer)
                .paymentMethod(PaymentMethod.COD)
                .itemsSubtotal(product.getPrice())
                .shippingTotal(BigDecimal.ZERO)
                .grandTotal(product.getPrice())
                .paymentStatus(OrderGroupPaymentStatus.PAID)
                .build());

        Order order = Order.builder()
                .code("VS-REV-" + uid)
                .orderGroup(group)
                .user(buyer)
                .shop(shop)
                .receiverName(buyer.getFullName())
                .receiverPhone("0912345678")
                .shippingAddress("Hà Nội")
                .shippingMethod(ShippingMethod.STANDARD)
                .itemsSubtotal(product.getPrice())
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(product.getPrice())
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(OrderPaymentStatus.PAID)
                .status(OrderStatus.DELIVERED)
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                .unit(product.getUnit())
                .unitPrice(product.getPrice())
                .quantity(1)
                .lineTotal(product.getPrice())
                .build();
        order.getItems().add(item);
        Order savedOrder = orderRepository.save(order);
        OrderItem savedItem = savedOrder.getItems().get(0);

        return reviewRepository.save(Review.builder()
                .orderItem(savedItem)
                .product(product)
                .user(buyer)
                .rating(rating)
                .content(content)
                .status(status)
                .build());
    }

    @Test
    @DisplayName("GET /seller/reviews và POST /seller/reviews/{id}/reply cho phép chủ shop trả lời và chặn IDOR")
    void testSellerReviewWebPageAndReplyWithIdor() throws Exception {
        User sellerA = createUser("rev_web_a", Role.SELLER);
        Shop shopA = createApprovedShop(sellerA, "revweba");
        User sellerB = createUser("rev_web_b", Role.SELLER);
        createApprovedShop(sellerB, "revwebb");
        User buyer = createUser("rev_web_buyer", Role.USER);

        Product productA = createProduct(shopA);
        Review review = createReviewForProduct(buyer, shopA, productA, 5, ReviewStatus.VISIBLE, "Cốm rất dẻo và thơm!");
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerADetails = new CustomUserDetails(sellerA);
        CustomUserDetails sellerBDetails = new CustomUserDetails(sellerB);

        // GET /seller/reviews
        mockMvc.perform(get("/seller/reviews").with(user(sellerADetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/reviews/index"))
                .andExpect(content().string(containsString("Cốm rất dẻo và thơm!")));

        // Cross-shop IDOR on web POST /seller/reviews/{id}/reply -> 302 /seller/reviews + flash errorMessage
        mockMvc.perform(post("/seller/reviews/" + review.getId() + "/reply")
                        .with(user(sellerBDetails))
                        .with(csrf())
                        .param("reply", "Phản hồi giả mạo từ shop khác"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/reviews"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertNull(reviewRepository.findById(review.getId()).orElseThrow().getSellerReply());

        // Owner reply -> 302 /seller/reviews + flash successMessage
        mockMvc.perform(post("/seller/reviews/" + review.getId() + "/reply")
                        .with(user(sellerADetails))
                        .with(csrf())
                        .param("reply", "Cảm ơn quý khách đã ủng hộ đặc sản Hà Nội!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/reviews"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Review updated = reviewRepository.findById(review.getId()).orElseThrow();
        assertEquals("Cảm ơn quý khách đã ủng hộ đặc sản Hà Nội!", updated.getSellerReply());
        assertNotNull(updated.getSellerRepliedAt());
    }

    @Test
    @DisplayName("POST /api/reviews/{id}/reply trả 200 cho chủ shop, 403 IDOR, 404 buyer không có shop, 401 guest, 400 validation")
    void testReviewReplyApiSecurityAndValidation() throws Exception {
        User sellerA = createUser("rev_api_a", Role.SELLER);
        Shop shopA = createApprovedShop(sellerA, "revapia");
        User sellerB = createUser("rev_api_b", Role.SELLER);
        createApprovedShop(sellerB, "revapib");
        User buyerNoShop = createUser("rev_api_buyer", Role.USER);

        Product productA = createProduct(shopA);
        Review review = createReviewForProduct(buyerNoShop, shopA, productA, 4, ReviewStatus.VISIBLE, "Đóng gói cẩn thận");
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerADetails = new CustomUserDetails(sellerA);
        CustomUserDetails sellerBDetails = new CustomUserDetails(sellerB);
        CustomUserDetails buyerDetails = new CustomUserDetails(buyerNoShop);

        // 1. Guest -> 401 JSON
        mockMvc.perform(post("/api/reviews/" + review.getId() + "/reply")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Guest reply\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        // 2. Buyer without shop -> 404 JSON (SHOP_NOT_FOUND)
        mockMvc.perform(post("/api/reviews/" + review.getId() + "/reply")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Buyer reply\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));

        // 3. Blank reply validation -> 400 JSON
        mockMvc.perform(post("/api/reviews/" + review.getId() + "/reply")
                        .with(user(sellerADetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors.reply").exists());

        // 4. Cross-shop seller IDOR -> 403 JSON
        mockMvc.perform(post("/api/reviews/" + review.getId() + "/reply")
                        .with(user(sellerBDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Cross shop reply\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // 5. Owner seller -> 200 JSON
        mockMvc.perform(post("/api/reviews/" + review.getId() + "/reply")
                        .with(user(sellerADetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reply\":\"Shop xin chân thành cảm ơn!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(review.getId()))
                .andExpect(jsonPath("$.data.sellerReply").value("Shop xin chân thành cảm ơn!"))
                .andExpect(jsonPath("$.data.sellerRepliedAt").isNotEmpty());
    }

    @Test
    @DisplayName("GET /api/reviews/product/{productId}?rating=5 chỉ trả về các đánh giá VISIBLE có đúng 5 sao")
    void testGetProductReviewsFilteredByRating() throws Exception {
        User seller = createUser("rev_flt_s", Role.SELLER);
        Shop shop = createApprovedShop(seller, "revflt");
        User buyer1 = createUser("rev_flt_b1", Role.USER);
        User buyer2 = createUser("rev_flt_b2", Role.USER);
        User buyer3 = createUser("rev_flt_b3", Role.USER);

        Product product = createProduct(shop);
        createReviewForProduct(buyer1, shop, product, 5, ReviewStatus.VISIBLE, "Xuất sắc 5 sao hiển thị");
        createReviewForProduct(buyer2, shop, product, 3, ReviewStatus.VISIBLE, "Tạm ổn 3 sao hiển thị");
        createReviewForProduct(buyer3, shop, product, 5, ReviewStatus.HIDDEN, "Đánh giá 5 sao đã bị ẩn");
        entityManager.flush();
        entityManager.clear();

        // Filter rating=5 -> only 1 VISIBLE 5-star review
        mockMvc.perform(get("/api/reviews/product/" + product.getId())
                        .param("rating", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].rating").value(5))
                .andExpect(jsonPath("$.data.content[0].content").value("Xuất sắc 5 sao hiển thị"));

        // Without rating filter -> returns 2 VISIBLE reviews (5-star and 3-star), excludes HIDDEN
        mockMvc.perform(get("/api/reviews/product/" + product.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)));
    }
}
