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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
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
class SellerOrderWebTest {

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
                .addressDetail("456 Phố Đặc Sản")
                .phone("0912345678")
                .status(ShopStatus.APPROVED)
                .build());
    }

    private Product createProduct(Shop shop, int initialStock) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0);
        return productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name("Đặc Sản Đơn Hàng " + uid)
                .slug("sp-ord-" + uid)
                .description("Mô tả đặc sản")
                .price(new BigDecimal("100000"))
                .stock(initialStock)
                .unit("Hộp")
                .originDetail("Huế")
                .status(ProductStatus.ACTIVE)
                .build());
    }

    private Order createOrder(User buyer, Shop shop, Product product, int qty, OrderStatus status) {
        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(qty));
        BigDecimal shipping = new BigDecimal("30000");
        BigDecimal total = subtotal.add(shipping);

        OrderGroup group = orderGroupRepository.save(OrderGroup.builder()
                .code("VSG-" + uid)
                .user(buyer)
                .paymentMethod(PaymentMethod.COD)
                .itemsSubtotal(subtotal)
                .shippingTotal(shipping)
                .grandTotal(total)
                .paymentStatus(OrderGroupPaymentStatus.NOT_REQUIRED)
                .build());

        Order order = Order.builder()
                .code("VS-" + uid)
                .orderGroup(group)
                .user(buyer)
                .shop(shop)
                .receiverName("Nguyễn Văn Mua")
                .receiverPhone("0912345678")
                .shippingAddress("12 Lê Lợi, Q1, TP.HCM")
                .shippingMethod(ShippingMethod.STANDARD)
                .itemsSubtotal(subtotal)
                .shippingFee(shipping)
                .totalAmount(total)
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(OrderPaymentStatus.UNPAID)
                .status(status)
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .productName(product.getName())
                .unit(product.getUnit())
                .unitPrice(product.getPrice())
                .quantity(qty)
                .lineTotal(subtotal)
                .build();
        order.getItems().add(item);

        return orderRepository.save(order);
    }

    @Test
    @DisplayName("GET /seller/orders/{code}, POST confirm và ship chuyển trạng thái PENDING_CONFIRM -> PREPARING -> SHIPPING")
    void testOrderDetailConfirmAndShipLifecycle() throws Exception {
        User seller = createUser("ord_s1", Role.SELLER);
        Shop shop = createApprovedShop(seller, "ords1");
        User buyer = createUser("ord_b1", Role.USER);
        Product product = createProduct(shop, 10);
        Order order = createOrder(buyer, shop, product, 2, OrderStatus.PENDING_CONFIRM);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);

        // Detail page
        mockMvc.perform(get("/seller/orders/" + order.getCode()).with(user(sellerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/orders/detail"))
                .andExpect(content().string(containsString(order.getCode())));

        // Confirm: PENDING_CONFIRM -> PREPARING
        mockMvc.perform(post("/seller/orders/" + order.getId() + "/confirm")
                        .with(user(sellerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderStatus.PREPARING, orderRepository.findById(order.getId()).orElseThrow().getStatus());

        // Ship: PREPARING -> SHIPPING
        mockMvc.perform(post("/seller/orders/" + order.getId() + "/ship")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("carrierName", "Viettel Post")
                        .param("trackingCode", "VTP-2026-999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();
        Order shipped = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.SHIPPING, shipped.getStatus());
        assertEquals("Viettel Post", shipped.getCarrierName());
        assertEquals("VTP-2026-999", shipped.getTrackingCode());
        assertNotNull(shipped.getShippedAt());
    }

    @Test
    @DisplayName("POST /seller/orders/{id}/cancel hủy đơn hợp lệ và hoàn lại tồn kho sản phẩm")
    void testCancelOrderRestoresProductStock() throws Exception {
        User seller = createUser("ord_s2", Role.SELLER);
        Shop shop = createApprovedShop(seller, "ords2");
        User buyer = createUser("ord_b2", Role.USER);
        Product product = createProduct(shop, 10);
        Order order = createOrder(buyer, shop, product, 3, OrderStatus.PENDING_CONFIRM);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);

        mockMvc.perform(post("/seller/orders/" + order.getId() + "/cancel")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("reason", "Hết nguyên liệu đóng gói"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Order cancelled = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, cancelled.getStatus());
        assertEquals("Hết nguyên liệu đóng gói", cancelled.getCancelReason());
        assertEquals(13, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    @DisplayName("Chặn chuyển trạng thái đơn hàng không hợp lệ (ship khi PENDING_CONFIRM, confirm/cancel khi SHIPPING hoặc DELIVERED)")
    void testInvalidOrderStatusTransitions() throws Exception {
        User seller = createUser("ord_s3", Role.SELLER);
        Shop shop = createApprovedShop(seller, "ords3");
        User buyer = createUser("ord_b3", Role.USER);
        Product product = createProduct(shop, 20);

        Order pendingOrder = createOrder(buyer, shop, product, 1, OrderStatus.PENDING_CONFIRM);
        Order shippingOrder = createOrder(buyer, shop, product, 1, OrderStatus.SHIPPING);
        Order deliveredOrder = createOrder(buyer, shop, product, 1, OrderStatus.DELIVERED);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);

        // Cannot ship directly from PENDING_CONFIRM
        mockMvc.perform(post("/seller/orders/" + pendingOrder.getId() + "/ship")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("carrierName", "GHN")
                        .param("trackingCode", "GHN111"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(OrderStatus.PENDING_CONFIRM, orderRepository.findById(pendingOrder.getId()).orElseThrow().getStatus());

        // Cannot confirm SHIPPING
        mockMvc.perform(post("/seller/orders/" + shippingOrder.getId() + "/confirm")
                        .with(user(sellerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(OrderStatus.SHIPPING, orderRepository.findById(shippingOrder.getId()).orElseThrow().getStatus());

        // Cannot cancel SHIPPING
        mockMvc.perform(post("/seller/orders/" + shippingOrder.getId() + "/cancel")
                        .with(user(sellerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(OrderStatus.SHIPPING, orderRepository.findById(shippingOrder.getId()).orElseThrow().getStatus());

        // Cannot cancel DELIVERED
        mockMvc.perform(post("/seller/orders/" + deliveredOrder.getId() + "/cancel")
                        .with(user(sellerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));
        assertEquals(OrderStatus.DELIVERED, orderRepository.findById(deliveredOrder.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Chống IDOR: Seller B không thể xem chi tiết, xác nhận, giao hàng hoặc hủy đơn của Seller A")
    void testCrossSellerOrderIdorProtection() throws Exception {
        User sellerA = createUser("ord_idor_a", Role.SELLER);
        Shop shopA = createApprovedShop(sellerA, "ordidora");
        User sellerB = createUser("ord_idor_b", Role.SELLER);
        createApprovedShop(sellerB, "ordidorb");
        User buyer = createUser("ord_idor_buyer", Role.USER);
        Product productA = createProduct(shopA, 15);
        Order orderA = createOrder(buyer, shopA, productA, 1, OrderStatus.PENDING_CONFIRM);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerBDetails = new CustomUserDetails(sellerB);

        // GET /{code} -> 400 error/500 ORDER_001
        mockMvc.perform(get("/seller/orders/" + orderA.getCode()).with(user(sellerBDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "ORDER_001"));

        // POST /{id}/confirm (numeric ID and code) -> 302 /seller/orders + flash errorMessage
        mockMvc.perform(post("/seller/orders/" + orderA.getId() + "/confirm")
                        .with(user(sellerBDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));

        mockMvc.perform(post("/seller/orders/" + orderA.getCode() + "/confirm")
                        .with(user(sellerBDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));

        // POST /{id}/ship
        mockMvc.perform(post("/seller/orders/" + orderA.getId() + "/ship")
                        .with(user(sellerBDetails))
                        .with(csrf())
                        .param("carrierName", "GHTK")
                        .param("trackingCode", "FAKE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));

        // POST /{id}/cancel
        mockMvc.perform(post("/seller/orders/" + orderA.getId() + "/cancel")
                        .with(user(sellerBDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/orders"))
                .andExpect(flash().attributeExists("errorMessage"));

        Order untouched = orderRepository.findById(orderA.getId()).orElseThrow();
        assertEquals(OrderStatus.PENDING_CONFIRM, untouched.getStatus());
        assertNull(untouched.getTrackingCode());
    }
}
