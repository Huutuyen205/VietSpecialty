package com.vietspecialty.admin;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class AdminModerationRegressionTest {

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
    private ProductCommentRepository productCommentRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-06: GET /admin/moderation?tab=reviews và ?tab=comments render 200 OK khi có dữ liệu thật (open-in-view=false)")
    void testAdminModerationReviewsAndCommentsWithoutLazyInitException() throws Exception {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User admin = null;
        User seller = null;
        User buyer = null;
        Shop shop = null;
        Product product = null;
        Order order = null;
        OrderGroup group = null;
        Review review = null;
        ProductComment comment = null;

        try {
            admin = userRepository.save(User.builder()
                    .fullName("Admin Mod Test " + uid)
                    .email("adm_mod_reg_" + uid + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@"))
                    .role(Role.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .build());

            seller = userRepository.save(User.builder()
                    .fullName("Seller Mod Test " + uid)
                    .email("sel_mod_reg_" + uid + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@"))
                    .role(Role.SELLER)
                    .status(UserStatus.ACTIVE)
                    .build());

            buyer = userRepository.save(User.builder()
                    .fullName("Nguyễn Reviewer " + uid)
                    .email("buy_mod_reg_" + uid + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@"))
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .build());

            Province province = provinceRepository.findAll().get(0);
            Category category = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0);

            shop = shopRepository.save(Shop.builder()
                    .name("Shop Mod Reg " + uid)
                    .slug("shop-mod-reg-" + uid)
                    .owner(seller)
                    .province(province)
                    .addressDetail("123 Đường Kiểm Duyệt")
                    .phone("0912345678")
                    .status(ShopStatus.APPROVED)
                    .build());

            product = productRepository.save(Product.builder()
                    .shop(shop)
                    .category(category)
                    .province(province)
                    .name("Đặc Sản Kiểm Duyệt " + uid)
                    .slug("dac-san-kiem-duyet-" + uid)
                    .description("Mô tả đặc sản kiểm duyệt")
                    .price(new BigDecimal("250000"))
                    .stock(10)
                    .unit("Hộp")
                    .originDetail("Thái Nguyên")
                    .status(ProductStatus.ACTIVE)
                    .build());

            group = orderGroupRepository.save(OrderGroup.builder()
                    .code("VSG-MOD-" + uid.toUpperCase())
                    .user(buyer)
                    .paymentMethod(PaymentMethod.COD)
                    .itemsSubtotal(new BigDecimal("250000"))
                    .shippingTotal(BigDecimal.ZERO)
                    .grandTotal(new BigDecimal("250000"))
                    .paymentStatus(OrderGroupPaymentStatus.PAID)
                    .build());

            Order orderDraft = Order.builder()
                    .code("VS-MOD-" + uid.toUpperCase())
                    .orderGroup(group)
                    .user(buyer)
                    .shop(shop)
                    .receiverName(buyer.getFullName())
                    .receiverPhone("0912345678")
                    .shippingAddress("Hà Nội")
                    .shippingMethod(ShippingMethod.STANDARD)
                    .itemsSubtotal(new BigDecimal("250000"))
                    .shippingFee(BigDecimal.ZERO)
                    .totalAmount(new BigDecimal("250000"))
                    .paymentMethod(PaymentMethod.COD)
                    .paymentStatus(OrderPaymentStatus.PAID)
                    .status(OrderStatus.DELIVERED)
                    .build();

            OrderItem item = OrderItem.builder()
                    .order(orderDraft)
                    .product(product)
                    .productName(product.getName())
                    .unit(product.getUnit())
                    .unitPrice(product.getPrice())
                    .quantity(1)
                    .lineTotal(product.getPrice())
                    .build();
            orderDraft.getItems().add(item);
            order = orderRepository.save(orderDraft);

            review = reviewRepository.save(Review.builder()
                    .orderItem(order.getItems().get(0))
                    .product(product)
                    .user(buyer)
                    .rating(5)
                    .content("Đánh giá rất ngon cho test DEF-06 " + uid)
                    .status(ReviewStatus.VISIBLE)
                    .build());

            comment = productCommentRepository.save(ProductComment.builder()
                    .product(product)
                    .user(buyer)
                    .content("Câu hỏi Q&A cho test DEF-06 " + uid)
                    .isSellerReply(false)
                    .status(CommentStatus.VISIBLE)
                    .build());

            CustomUserDetails adminDetails = new CustomUserDetails(admin);

            mockMvc.perform(get("/admin/moderation")
                            .with(user(adminDetails))
                            .param("tab", "reviews"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/moderation/index"))
                    .andExpect(content().string(containsString("Nguyễn Reviewer " + uid)))
                    .andExpect(content().string(containsString("Đặc Sản Kiểm Duyệt " + uid)));

            mockMvc.perform(get("/admin/moderation")
                            .with(user(adminDetails))
                            .param("tab", "comments"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("admin/moderation/index"))
                    .andExpect(content().string(containsString("test DEF-06 " + uid)));
        } finally {
            if (review != null && review.getId() != null) reviewRepository.deleteById(review.getId());
            if (comment != null && comment.getId() != null) productCommentRepository.deleteById(comment.getId());
            if (order != null && order.getId() != null) orderRepository.deleteById(order.getId());
            if (group != null && group.getId() != null) orderGroupRepository.deleteById(group.getId());
            if (product != null && product.getId() != null) productRepository.deleteById(product.getId());
            if (shop != null && shop.getId() != null) shopRepository.deleteById(shop.getId());
            if (buyer != null && buyer.getId() != null) userRepository.deleteById(buyer.getId());
            if (seller != null && seller.getId() != null) userRepository.deleteById(seller.getId());
            if (admin != null && admin.getId() != null) userRepository.deleteById(admin.getId());
        }
    }
}
