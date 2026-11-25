package com.vietspecialty.order;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class OrderRefundAndWishlistRegressionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private WishlistRepository wishlistRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private OrderGroupRepository orderGroupRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    private Product createProduct(Shop shop, Province province, Category category, String prefix, ProductStatus status, int stock) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Product p = productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name("Đặc sản " + prefix + " " + uid)
                .slug("dac-san-" + prefix + "-" + uid)
                .price(BigDecimal.valueOf(150_000))
                .stock(stock)
                .unit("Hộp 500g")
                .status(status)
                .build());
        productImageRepository.save(ProductImage.builder()
                .product(p)
                .imageUrl("/images/placeholder.jpg")
                .isPrimary(true)
                .sortOrder(0)
                .build());
        return p;
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-03 (Non-Transactional): Wishlist lưu DB bền vững qua session, JOIN FETCH dưới open-in-view=false và chặn productId không tồn tại")
    void testWishlistDbPersistenceAndRendering() throws Exception {
        User buyer = null;
        Product activeProduct = null;
        Product guestSessionProduct = null;
        try {
            buyer = createUser("wl_buyer", Role.USER);
            CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

            Shop shop = shopRepository.findAll().stream()
                    .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                    .findFirst().orElseThrow();
            Province province = provinceRepository.findAll().get(0);
            Category category = categoryRepository.findAll().get(0);

            activeProduct = createProduct(shop, province, category, "wl1", ProductStatus.ACTIVE, 10);
            guestSessionProduct = createProduct(shop, province, category, "wl2", ProductStatus.ACTIVE, 10);

            // 1. Toggle non-existent productId -> success=false (no 500 DataIntegrityViolation)
            mockMvc.perform(post("/wishlist/toggle/999999999")
                            .with(user(buyerDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false));

            // 2. Guest toggles guestSessionProduct in HttpSession
            MockHttpSession sharedSession = new MockHttpSession();
            mockMvc.perform(post("/wishlist/toggle/" + guestSessionProduct.getId())
                            .session(sharedSession)
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.added").value(true));

            // 3. Authenticated user toggles activeProduct via body endpoint (merges guestSessionProduct into DB)
            mockMvc.perform(post("/wishlist/toggle")
                            .session(sharedSession)
                            .with(user(buyerDetails))
                            .with(csrf())
                            .param("productId", String.valueOf(activeProduct.getId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.added").value(true))
                    .andExpect(jsonPath("$.count").value(2));

            assertTrue(wishlistRepository.existsByUserIdAndProductId(buyer.getId(), activeProduct.getId()));
            assertTrue(wishlistRepository.existsByUserIdAndProductId(buyer.getId(), guestSessionProduct.getId()));

            // 4. Fresh session (simulating new browser/restart) still renders both DB wishlist products without LazyInitializationException
            mockMvc.perform(get("/wishlist")
                            .with(user(buyerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("web/wishlist"))
                    .andExpect(model().attribute("wishlistProducts", hasSize(2)))
                    .andExpect(content().string(containsString(activeProduct.getName())))
                    .andExpect(content().string(containsString(guestSessionProduct.getName())));

            // 5. Toggle activeProduct again -> removed from DB
            mockMvc.perform(post("/wishlist/toggle/" + activeProduct.getId())
                            .with(user(buyerDetails))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.added").value(false))
                    .andExpect(jsonPath("$.count").value(1));
            assertFalse(wishlistRepository.existsByUserIdAndProductId(buyer.getId(), activeProduct.getId()));

            // 6. Clear wishlist -> deletes all DB rows for buyer
            mockMvc.perform(post("/wishlist/clear")
                            .with(user(buyerDetails))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/wishlist"));
            assertTrue(wishlistRepository.findProductIdsByUserId(buyer.getId()).isEmpty());
        } finally {
            if (buyer != null && buyer.getId() != null) {
                wishlistRepository.deleteAllByUserId(buyer.getId());
            }
            if (activeProduct != null && activeProduct.getId() != null) {
                productRepository.deleteById(activeProduct.getId());
            }
            if (guestSessionProduct != null && guestSessionProduct.getId() != null) {
                productRepository.deleteById(guestSessionProduct.getId());
            }
            if (buyer != null && buyer.getId() != null) {
                userRepository.deleteById(buyer.getId());
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-04 (Non-Transactional): Reorder bỏ qua sản phẩm HIDDEN/hết hàng mà không gây UnexpectedRollbackException 500")
    void testReorderSkipsHiddenProductWithoutUnexpectedRollback() throws Exception {
        User buyer = null;
        Product activeProd = null;
        Product hiddenProd = null;
        OrderGroup group = null;
        try {
            buyer = createUser("reord_buyer", Role.USER);
            CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

            Shop shop = shopRepository.findAll().stream()
                    .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                    .findFirst().orElseThrow();
            Province province = provinceRepository.findAll().get(0);
            Category category = categoryRepository.findAll().get(0);

            activeProd = createProduct(shop, province, category, "reord_act", ProductStatus.ACTIVE, 10);
            hiddenProd = createProduct(shop, province, category, "reord_hid", ProductStatus.HIDDEN, 10);

            String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            group = orderGroupRepository.save(OrderGroup.builder()
                    .code("OGREORD" + uid)
                    .user(buyer)
                    .paymentMethod(PaymentMethod.COD)
                    .itemsSubtotal(BigDecimal.valueOf(300_000))
                    .shippingTotal(BigDecimal.valueOf(30_000))
                    .grandTotal(BigDecimal.valueOf(330_000))
                    .paymentStatus(OrderGroupPaymentStatus.UNPAID)
                    .build());

            Order mixedOrder = orderRepository.save(Order.builder()
                    .code("ORDMIX" + uid)
                    .orderGroup(group)
                    .user(buyer)
                    .shop(shop)
                    .receiverName("Buyer")
                    .receiverPhone("0912345678")
                    .shippingAddress("123 Lê Lợi")
                    .shippingMethod(ShippingMethod.STANDARD)
                    .itemsSubtotal(BigDecimal.valueOf(300_000))
                    .shippingFee(BigDecimal.valueOf(30_000))
                    .totalAmount(BigDecimal.valueOf(330_000))
                    .paymentMethod(PaymentMethod.COD)
                    .paymentStatus(OrderPaymentStatus.UNPAID)
                    .status(OrderStatus.DELIVERED)
                    .build());

            orderItemRepository.save(OrderItem.builder()
                    .order(mixedOrder)
                    .product(activeProd)
                    .productName(activeProd.getName())
                    .unit(activeProd.getUnit())
                    .unitPrice(activeProd.getPrice())
                    .quantity(2)
                    .lineTotal(BigDecimal.valueOf(300_000))
                    .build());

            orderItemRepository.save(OrderItem.builder()
                    .order(mixedOrder)
                    .product(hiddenProd)
                    .productName(hiddenProd.getName())
                    .unit(hiddenProd.getUnit())
                    .unitPrice(hiddenProd.getPrice())
                    .quantity(1)
                    .lineTotal(BigDecimal.valueOf(150_000))
                    .build());

            // 1. HTML reorder succeeds with redirect:/cart and adds only activeProd
            mockMvc.perform(post("/orders/" + mixedOrder.getId() + "/reorder")
                            .with(user(buyerDetails))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/cart"));

            List<CartItem> cart = cartItemRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId());
            assertEquals(1, cart.size());
            assertEquals(activeProd.getId(), cart.get(0).getProduct().getId());
            assertEquals(2, cart.get(0).getQuantity());

            // 2. Order with ONLY HIDDEN product: HTML redirects /cart, JSON returns 400
            Order hiddenOnlyOrder = orderRepository.save(Order.builder()
                    .code("ORDHID" + uid)
                    .orderGroup(group)
                    .user(buyer)
                    .shop(shop)
                    .receiverName("Buyer")
                    .receiverPhone("0912345678")
                    .shippingAddress("123 Lê Lợi")
                    .shippingMethod(ShippingMethod.STANDARD)
                    .itemsSubtotal(BigDecimal.valueOf(150_000))
                    .shippingFee(BigDecimal.valueOf(30_000))
                    .totalAmount(BigDecimal.valueOf(180_000))
                    .paymentMethod(PaymentMethod.COD)
                    .paymentStatus(OrderPaymentStatus.UNPAID)
                    .status(OrderStatus.CANCELLED)
                    .build());

            orderItemRepository.save(OrderItem.builder()
                    .order(hiddenOnlyOrder)
                    .product(hiddenProd)
                    .productName(hiddenProd.getName())
                    .unit(hiddenProd.getUnit())
                    .unitPrice(hiddenProd.getPrice())
                    .quantity(1)
                    .lineTotal(BigDecimal.valueOf(150_000))
                    .build());

            mockMvc.perform(post("/orders/" + hiddenOnlyOrder.getId() + "/reorder")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .header("Accept", "application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        } finally {
            if (buyer != null && buyer.getId() != null) {
                cartItemRepository.deleteAll(cartItemRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId()));
            }
            if (group != null && group.getId() != null) {
                orderRepository.deleteAll(orderRepository.findByOrderGroupId(group.getId()));
                orderGroupRepository.deleteById(group.getId());
            }
            if (activeProd != null && activeProd.getId() != null) {
                productRepository.deleteById(activeProd.getId());
            }
            if (hiddenProd != null && hiddenProd.getId() != null) {
                productRepository.deleteById(hiddenProd.getId());
            }
            if (buyer != null && buyer.getId() != null) {
                userRepository.deleteById(buyer.getId());
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-07 (Non-Transactional): Refund-request chặn đơn UNPAID/đang giao và chỉ cho phép đơn PAID + DELIVERED/CANCELLED")
    void testRefundRequestEligibilityGuards() throws Exception {
        User buyer = null;
        User attacker = null;
        OrderGroup group = null;
        List<Order> createdOrders = new ArrayList<>();
        try {
            buyer = createUser("ref_buyer", Role.USER);
            attacker = createUser("ref_attacker", Role.USER);
            CustomUserDetails buyerDetails = new CustomUserDetails(buyer);
            CustomUserDetails attackerDetails = new CustomUserDetails(attacker);

            Shop shop = shopRepository.findAll().stream()
                    .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                    .findFirst().orElseThrow();

            String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            group = orderGroupRepository.save(OrderGroup.builder()
                    .code("OGREF" + uid)
                    .user(buyer)
                    .paymentMethod(PaymentMethod.VNPAY)
                    .itemsSubtotal(BigDecimal.valueOf(200_000))
                    .shippingTotal(BigDecimal.valueOf(30_000))
                    .grandTotal(BigDecimal.valueOf(230_000))
                    .paymentStatus(OrderGroupPaymentStatus.UNPAID)
                    .build());

            Order unpaidCodOrder = orderRepository.save(Order.builder()
                    .code("ORDUNP" + uid)
                    .orderGroup(group)
                    .user(buyer)
                    .shop(shop)
                    .receiverName("Buyer")
                    .receiverPhone("0912345678")
                    .shippingAddress("123 Lê Lợi")
                    .shippingMethod(ShippingMethod.STANDARD)
                    .itemsSubtotal(BigDecimal.valueOf(200_000))
                    .shippingFee(BigDecimal.valueOf(30_000))
                    .totalAmount(BigDecimal.valueOf(230_000))
                    .paymentMethod(PaymentMethod.COD)
                    .paymentStatus(OrderPaymentStatus.UNPAID)
                    .status(OrderStatus.PENDING_CONFIRM)
                    .build());
            createdOrders.add(unpaidCodOrder);

            Order paidActiveShipping = orderRepository.save(Order.builder()
                    .code("ORDSHP" + uid)
                    .orderGroup(group)
                    .user(buyer)
                    .shop(shop)
                    .receiverName("Buyer")
                    .receiverPhone("0912345678")
                    .shippingAddress("123 Lê Lợi")
                    .shippingMethod(ShippingMethod.STANDARD)
                    .itemsSubtotal(BigDecimal.valueOf(200_000))
                    .shippingFee(BigDecimal.valueOf(30_000))
                    .totalAmount(BigDecimal.valueOf(230_000))
                    .paymentMethod(PaymentMethod.VNPAY)
                    .paymentStatus(OrderPaymentStatus.PAID)
                    .status(OrderStatus.SHIPPING)
                    .build());
            createdOrders.add(paidActiveShipping);

            Order paidDelivered = orderRepository.save(Order.builder()
                    .code("ORDDLV" + uid)
                    .orderGroup(group)
                    .user(buyer)
                    .shop(shop)
                    .receiverName("Buyer")
                    .receiverPhone("0912345678")
                    .shippingAddress("123 Lê Lợi")
                    .shippingMethod(ShippingMethod.STANDARD)
                    .itemsSubtotal(BigDecimal.valueOf(200_000))
                    .shippingFee(BigDecimal.valueOf(30_000))
                    .totalAmount(BigDecimal.valueOf(230_000))
                    .paymentMethod(PaymentMethod.VNPAY)
                    .paymentStatus(OrderPaymentStatus.PAID)
                    .status(OrderStatus.DELIVERED)
                    .build());
            createdOrders.add(paidDelivered);

            Order paidCancelled = orderRepository.save(Order.builder()
                    .code("ORDCXL" + uid)
                    .orderGroup(group)
                    .user(buyer)
                    .shop(shop)
                    .receiverName("Buyer")
                    .receiverPhone("0912345678")
                    .shippingAddress("123 Lê Lợi")
                    .shippingMethod(ShippingMethod.STANDARD)
                    .itemsSubtotal(BigDecimal.valueOf(200_000))
                    .shippingFee(BigDecimal.valueOf(30_000))
                    .totalAmount(BigDecimal.valueOf(230_000))
                    .paymentMethod(PaymentMethod.VNPAY)
                    .paymentStatus(OrderPaymentStatus.PAID)
                    .status(OrderStatus.CANCELLED)
                    .build());
            createdOrders.add(paidCancelled);

            // 1. Non-owner attacker still gets ACCESS_DENIED (400 error/500) before eligibility guard
            mockMvc.perform(post("/orders/" + unpaidCodOrder.getId() + "/refund-request")
                            .with(user(attackerDetails))
                            .with(csrf()))
                    .andExpect(status().isBadRequest())
                    .andExpect(view().name("error/500"));

            // 2. UNPAID COD order rejected (JSON 400 and HTML redirect + flash errorMessage)
            mockMvc.perform(post("/orders/" + unpaidCodOrder.getId() + "/refund-request")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .header("Accept", "application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            mockMvc.perform(post("/orders/" + unpaidCodOrder.getId() + "/refund-request")
                            .with(user(buyerDetails))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/orders/" + unpaidCodOrder.getCode()))
                    .andExpect(flash().attributeExists("errorMessage"));
            assertEquals(OrderPaymentStatus.UNPAID,
                    orderRepository.findById(unpaidCodOrder.getId()).orElseThrow().getPaymentStatus());

            // 3. PAID but active SHIPPING order rejected
            mockMvc.perform(post("/orders/" + paidActiveShipping.getId() + "/refund-request")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .header("Accept", "application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
            assertEquals(OrderPaymentStatus.PAID,
                    orderRepository.findById(paidActiveShipping.getId()).orElseThrow().getPaymentStatus());

            // 4. PAID + DELIVERED succeeds -> REFUND_PENDING, duplicate rejected
            mockMvc.perform(post("/orders/" + paidDelivered.getId() + "/refund-request")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .header("Accept", "application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            assertEquals(OrderPaymentStatus.REFUND_PENDING,
                    orderRepository.findById(paidDelivered.getId()).orElseThrow().getPaymentStatus());

            mockMvc.perform(post("/orders/" + paidDelivered.getId() + "/refund-request")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .header("Accept", "application/json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));

            // 5. PAID + CANCELLED succeeds -> REFUND_PENDING
            mockMvc.perform(post("/orders/" + paidCancelled.getId() + "/refund-request")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .header("Accept", "application/json"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
            assertEquals(OrderPaymentStatus.REFUND_PENDING,
                    orderRepository.findById(paidCancelled.getId()).orElseThrow().getPaymentStatus());
        } finally {
            if (group != null && group.getId() != null) {
                orderRepository.deleteAll(orderRepository.findByOrderGroupId(group.getId()));
                orderGroupRepository.deleteById(group.getId());
            } else if (!createdOrders.isEmpty()) {
                for (Order o : createdOrders) {
                    if (o != null && o.getId() != null) {
                        orderRepository.findById(o.getId()).ifPresent(orderRepository::delete);
                    }
                }
            }
            if (buyer != null && buyer.getId() != null) {
                userRepository.deleteById(buyer.getId());
            }
            if (attacker != null && attacker.getId() != null) {
                userRepository.deleteById(attacker.getId());
            }
        }
    }
}
