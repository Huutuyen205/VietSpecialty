package com.vietspecialty.order;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.request.ShopPackageGiftRequest;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.OrderService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
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
class OrderGiftFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private OrderGroupRepository orderGroupRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

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

    private Address createDefaultAddress(User buyer, String prefix) {
        Province province = provinceRepository.findAll().get(0);
        return addressRepository.save(Address.builder()
                .user(buyer)
                .receiverName("Người Nhận " + prefix)
                .receiverPhone("0912345678")
                .province(province)
                .ward("Phường Bến Nghé")
                .streetAddress("12 Lê Lợi")
                .isDefault(true)
                .build());
    }

    private Shop createApprovedShop(User owner, String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Province province = provinceRepository.findAll().get(0);
        return shopRepository.save(Shop.builder()
                .name("HTX Quà " + prefix + " " + uid)
                .slug("htx-qua-" + prefix + "-" + uid)
                .owner(owner)
                .province(province)
                .addressDetail("123 Đường Đặc Sản")
                .phone("0987654321")
                .status(ShopStatus.APPROVED)
                .build());
    }

    private Product createProduct(Shop shop, String prefix, BigDecimal price, int stock) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.findAll().get(0);
        Province province = provinceRepository.findAll().get(0);
        return productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name("Đặc sản " + prefix + " " + uid)
                .slug("dac-san-" + prefix + "-" + uid)
                .price(price)
                .stock(stock)
                .unit("Hộp 500g")
                .status(ProductStatus.ACTIVE)
                .build());
    }

    @Test
    @DisplayName("GET /checkout hiển thị khu vực gói quà & thiệp chúc mừng theo từng shop")
    void testCheckoutRendersGiftControls() throws Exception {
        User seller = createUser("sel_ui", Role.SELLER);
        Shop shop = createApprovedShop(seller, "ui");
        Product product = createProduct(shop, "tra", BigDecimal.valueOf(200_000), 10);

        User buyer = createUser("buy_ui", Role.USER);
        createDefaultAddress(buyer, "ui");
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));

        mockMvc.perform(get("/checkout")
                        .with(user(new CustomUserDetails(buyer))))
                .andExpect(status().isOk())
                .andExpect(view().name("web/checkout"))
                .andExpect(content().string(containsString("shopGifts[" + shop.getId() + "].isGift")))
                .andExpect(content().string(containsString("Gói làm quà biếu")))
                .andExpect(content().string(containsString("STANDARD_BOX")))
                .andExpect(content().string(containsString("BAMBOO_PREMIUM")));
    }

    @Test
    @DisplayName("POST /checkout/place-order: STANDARD_BOX (0đ) và BAMBOO_PREMIUM (+25.000đ) + VNPay vnp_Amount")
    void testPlaceOrderStandardAndBambooGiftWithVnpay() throws Exception {
        User seller = createUser("sel_gift", Role.SELLER);
        Shop shop = createApprovedShop(seller, "gift");
        Product p1 = createProduct(shop, "sen", BigDecimal.valueOf(200_000), 20);

        User buyer = createUser("buy_gift", Role.USER);
        Address addr = createDefaultAddress(buyer, "gift");
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        // 1. STANDARD_BOX (0₫) via COD
        cartService.addToCart(buyer.getId(), new CartItemRequest(p1.getId(), 1));
        CheckoutPreviewResponse previewStd = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);

        var stdResult = mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", previewStd.checkoutToken())
                        .param("shopGifts[" + shop.getId() + "].isGift", "true")
                        .param("shopGifts[" + shop.getId() + "].giftOccasion", "FAMILY")
                        .param("shopGifts[" + shop.getId() + "].giftWrapType", "STANDARD_BOX")
                        .param("shopGifts[" + shop.getId() + "].giftRecipientName", "Bố mẹ kính yêu")
                        .param("shopGifts[" + shop.getId() + "].giftMessage", "Con gửi quà quê biếu bố mẹ"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/checkout/success?groupCode=")))
                .andReturn();

        String stdGroupCode = stdResult.getResponse().getRedirectedUrl().substring("/checkout/success?groupCode=".length());
        entityManager.flush();
        entityManager.clear();

        OrderGroup stdGroup = orderGroupRepository.findByCode(stdGroupCode).orElseThrow();
        // 200,000 + 30,000 ship + 0 gift = 230,000
        assertEquals(0, BigDecimal.valueOf(230_000).compareTo(stdGroup.getGrandTotal()));
        Order stdOrder = orderRepository.findByOrderGroupId(stdGroup.getId()).get(0);
        assertTrue(Boolean.TRUE.equals(stdOrder.getIsGift()));
        assertEquals(GiftOccasion.FAMILY, stdOrder.getGiftOccasion());
        assertEquals(GiftWrapType.STANDARD_BOX, stdOrder.getGiftWrapType());
        assertEquals(0, BigDecimal.ZERO.compareTo(stdOrder.getGiftFee()));
        assertEquals(0, BigDecimal.valueOf(230_000).compareTo(stdOrder.getTotalAmount()));
        assertEquals("Bố mẹ kính yêu", stdOrder.getGiftRecipientName());
        assertEquals("Con gửi quà quê biếu bố mẹ", stdOrder.getGiftMessage());

        // 2. BAMBOO_PREMIUM (+25,000₫) via VNPAY -> vnp_Amount = (200,000 + 30,000 + 25,000) * 100 = 25,500,000
        cartService.addToCart(buyer.getId(), new CartItemRequest(p1.getId(), 1));
        CheckoutPreviewResponse previewBamboo = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);

        var vnpResult = mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "VNPAY")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", previewBamboo.checkoutToken())
                        .param("shopGifts[" + shop.getId() + "].isGift", "true")
                        .param("shopGifts[" + shop.getId() + "].giftOccasion", "tet")
                        .param("shopGifts[" + shop.getId() + "].giftWrapType", "bamboo_premium")
                        .param("shopGifts[" + shop.getId() + "].giftRecipientName", "  Ông Bà Nội  ")
                        .param("shopGifts[" + shop.getId() + "].giftMessage", "  Chúc mừng năm mới an khang!  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://sandbox.vnpayment.vn/")))
                .andReturn();

        String vnpUrl = vnpResult.getResponse().getRedirectedUrl();
        assertNotNull(vnpUrl);
        assertTrue(vnpUrl.contains("vnp_Amount=25500000"), "Expected vnp_Amount=25500000 in URL: " + vnpUrl);

        entityManager.flush();
        entityManager.clear();

        OrderGroup bambooGroup = orderGroupRepository.findAll().stream()
                .filter(g -> g.getUser().getId().equals(buyer.getId()) && g.getPaymentMethod() == PaymentMethod.VNPAY)
                .findFirst().orElseThrow();
        assertEquals(0, BigDecimal.valueOf(255_000).compareTo(bambooGroup.getGrandTotal()));

        Payment payment = paymentRepository.findTopByOrderGroupIdOrderByCreatedAtDesc(bambooGroup.getId()).orElseThrow();
        assertEquals(0, BigDecimal.valueOf(255_000).compareTo(payment.getAmount()));

        Order bambooOrder = orderRepository.findByOrderGroupId(bambooGroup.getId()).get(0);
        assertTrue(Boolean.TRUE.equals(bambooOrder.getIsGift()));
        assertEquals(GiftOccasion.TET, bambooOrder.getGiftOccasion());
        assertEquals(GiftWrapType.BAMBOO_PREMIUM, bambooOrder.getGiftWrapType());
        assertEquals(0, BigDecimal.valueOf(25_000).compareTo(bambooOrder.getGiftFee()));
        assertEquals(0, BigDecimal.valueOf(255_000).compareTo(bambooOrder.getTotalAmount()));
        assertEquals("Ông Bà Nội", bambooOrder.getGiftRecipientName());
        assertEquals("Chúc mừng năm mới an khang!", bambooOrder.getGiftMessage());
    }

    @Test
    @DisplayName("Đa gian hàng kết hợp: Shop A gói quà tre đan (+25k) + voucher shop, Shop B không gói quà")
    void testMultiShopMixedGiftAndVoucherMath() throws Exception {
        User sellerA = createUser("sel_ma", Role.SELLER);
        User sellerB = createUser("sel_mb", Role.SELLER);
        Shop shopA = createApprovedShop(sellerA, "ma");
        Shop shopB = createApprovedShop(sellerB, "mb");

        // Shop A: 480,000đ (< 500,000đ threshold -> +30k ship EVEN WITH +25k gift fee!)
        Product prodA = createProduct(shopA, "traA", BigDecimal.valueOf(480_000), 10);
        // Shop B: 200,000đ (< 500,000đ threshold -> +30k ship)
        Product prodB = createProduct(shopB, "matB", BigDecimal.valueOf(200_000), 10);

        String voucherCode = "GIFT" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        voucherRepository.save(Voucher.builder()
                .code(voucherCode)
                .name("Giảm 40k Shop A")
                .scope(VoucherScope.SHOP)
                .shop(shopA)
                .createdBy(sellerA)
                .discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(40_000))
                .minOrderValue(BigDecimal.valueOf(450_000))
                .usageLimit(10)
                .usedCount(0)
                .isActive(true)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(10))
                .build());

        User buyer = createUser("buy_multi", Role.USER);
        Address addr = createDefaultAddress(buyer, "multi");
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        cartService.addToCart(buyer.getId(), new CartItemRequest(prodA.getId(), 1));
        cartService.addToCart(buyer.getId(), new CartItemRequest(prodB.getId(), 1));

        CheckoutPreviewResponse preview = orderService.previewCheckout(
                buyer.getId(), "STANDARD", null, Map.of(shopA.getId(), voucherCode));

        var res = mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("shopVoucherCodes[" + shopA.getId() + "]", voucherCode)
                        .param("checkoutToken", preview.checkoutToken())
                        .param("shopGifts[" + shopA.getId() + "].isGift", "true")
                        .param("shopGifts[" + shopA.getId() + "].giftOccasion", "PARTNER")
                        .param("shopGifts[" + shopA.getId() + "].giftWrapType", "BAMBOO_PREMIUM")
                        .param("shopGifts[" + shopA.getId() + "].giftRecipientName", "Đối tác Minh Anh")
                        .param("shopGifts[" + shopA.getId() + "].giftMessage", "Trân trọng cảm ơn sự đồng hành")
                        .param("shopGifts[" + shopB.getId() + "].isGift", "false")
                        .param("shopGifts[" + shopB.getId() + "].giftOccasion", "TET")
                        .param("shopGifts[" + shopB.getId() + "].giftWrapType", "BAMBOO_PREMIUM")
                        .param("shopGifts[" + shopB.getId() + "].giftRecipientName", "Should Be Ignored")
                        .param("shopGifts[" + shopB.getId() + "].giftMessage", "Should Be Ignored"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/checkout/success?groupCode=")))
                .andReturn();

        String groupCode = res.getResponse().getRedirectedUrl().substring("/checkout/success?groupCode=".length());
        entityManager.flush();
        entityManager.clear();

        OrderGroup group = orderGroupRepository.findByCode(groupCode).orElseThrow();
        List<Order> orders = orderRepository.findByOrderGroupId(group.getId());
        assertEquals(2, orders.size());

        Order orderA = orders.stream().filter(o -> o.getShop().getId().equals(shopA.getId())).findFirst().orElseThrow();
        Order orderB = orders.stream().filter(o -> o.getShop().getId().equals(shopB.getId())).findFirst().orElseThrow();

        // Shop A: 480,000 + 30,000 ship (subtotal 480k < 500k even with +25k gift!) + 25,000 gift - 40,000 disc = 495,000
        assertTrue(Boolean.TRUE.equals(orderA.getIsGift()));
        assertEquals(0, BigDecimal.valueOf(30_000).compareTo(orderA.getShippingFee()));
        assertEquals(0, BigDecimal.valueOf(25_000).compareTo(orderA.getGiftFee()));
        assertEquals(0, BigDecimal.valueOf(40_000).compareTo(orderA.getShopDiscount()));
        assertEquals(0, BigDecimal.valueOf(495_000).compareTo(orderA.getTotalAmount()));

        // Shop B: isGift=false -> normalized to non-gift, 200,000 + 30,000 = 230,000
        assertFalse(Boolean.TRUE.equals(orderB.getIsGift()));
        assertNull(orderB.getGiftOccasion());
        assertNull(orderB.getGiftWrapType());
        assertEquals(0, BigDecimal.ZERO.compareTo(orderB.getGiftFee()));
        assertNull(orderB.getGiftRecipientName());
        assertNull(orderB.getGiftMessage());
        assertEquals(0, BigDecimal.valueOf(230_000).compareTo(orderB.getTotalAmount()));

        // Group grandTotal = 495,000 + 230,000 = 725,000
        assertEquals(0, BigDecimal.valueOf(725_000).compareTo(group.getGrandTotal()));
    }

    @Test
    @DisplayName("Bảo mật & Validate: isGift=false bỏ qua phí giả mạo, từ chối dữ liệu quà không hợp lệ mà không trừ kho")
    void testGiftSecurityAndValidationRejections() throws Exception {
        User seller = createUser("sel_val", Role.SELLER);
        Shop shop = createApprovedShop(seller, "val");
        Product product = createProduct(shop, "banh", BigDecimal.valueOf(150_000), 10);

        User buyer = createUser("buy_val", Role.USER);
        Address addr = createDefaultAddress(buyer, "val");
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        // 1. Tampered extra giftFee params when isGift=false -> completely ignored
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse p1 = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        var resTamper = mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", p1.checkoutToken())
                        .param("giftFee", "999999")
                        .param("shopGifts[" + shop.getId() + "].isGift", "false")
                        .param("shopGifts[" + shop.getId() + "].giftFee", "1")
                        .param("shopGifts[" + shop.getId() + "].giftOccasion", "TET")
                        .param("shopGifts[" + shop.getId() + "].giftWrapType", "BAMBOO_PREMIUM"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/checkout/success?groupCode=")))
                .andReturn();

        String tamperGroupCode = resTamper.getResponse().getRedirectedUrl().substring("/checkout/success?groupCode=".length());
        entityManager.flush();
        entityManager.clear();
        Order tamperOrder = orderRepository.findByOrderGroupId(orderGroupRepository.findByCode(tamperGroupCode).orElseThrow().getId()).get(0);
        assertFalse(Boolean.TRUE.equals(tamperOrder.getIsGift()));
        assertEquals(0, BigDecimal.ZERO.compareTo(tamperOrder.getGiftFee()));
        assertEquals(0, BigDecimal.valueOf(180_000).compareTo(tamperOrder.getTotalAmount()));
        assertEquals(9, productRepository.findById(product.getId()).orElseThrow().getStock());

        // 2. Service-level validation rejections BEFORE stock decrement
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 2));
        int stockBefore = productRepository.findById(product.getId()).orElseThrow().getStock();

        // 2a. Blank occasion
        CheckoutPreviewResponse pBlankOcc = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        BusinessException exBlank = assertThrows(BusinessException.class, () ->
                orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                        addr.getId(), "COD", "STANDARD", null, null, pBlankOcc.checkoutToken(), null,
                        Map.of(shop.getId(), new ShopPackageGiftRequest(true, "  ", "STANDARD_BOX", "An", "Chúc vui"))
                ), "127.0.0.1"));
        assertEquals(ErrorCode.VALIDATION_ERROR, exBlank.getErrorCode());

        // 2b. Invalid wrapType
        CheckoutPreviewResponse pBadWrap = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        BusinessException exWrap = assertThrows(BusinessException.class, () ->
                orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                        addr.getId(), "COD", "STANDARD", null, null, pBadWrap.checkoutToken(), null,
                        Map.of(shop.getId(), new ShopPackageGiftRequest(true, "TET", "GOLD_BOX", "An", "Chúc vui"))
                ), "127.0.0.1"));
        assertEquals(ErrorCode.VALIDATION_ERROR, exWrap.getErrorCode());

        // 2c. Recipient > 100 chars
        CheckoutPreviewResponse pLongRecip = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        BusinessException exRecip = assertThrows(BusinessException.class, () ->
                orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                        addr.getId(), "COD", "STANDARD", null, null, pLongRecip.checkoutToken(), null,
                        Map.of(shop.getId(), new ShopPackageGiftRequest(true, "CELEBRATION", "STANDARD_BOX", "A".repeat(101), "Chúc vui"))
                ), "127.0.0.1"));
        assertEquals(ErrorCode.VALIDATION_ERROR, exRecip.getErrorCode());

        // 2d. Message > 300 chars
        CheckoutPreviewResponse pLongMsg = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        BusinessException exMsg = assertThrows(BusinessException.class, () ->
                orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                        addr.getId(), "COD", "STANDARD", null, null, pLongMsg.checkoutToken(), null,
                        Map.of(shop.getId(), new ShopPackageGiftRequest(true, "PARTNER", "BAMBOO_PREMIUM", "An", "M".repeat(301)))
                ), "127.0.0.1"));
        assertEquals(ErrorCode.VALIDATION_ERROR, exMsg.getErrorCode());

        // Verify stock was NEVER decremented across all 4 rejected attempts
        assertEquals(stockBefore, productRepository.findById(product.getId()).orElseThrow().getStock());

        // 3. MockMvc invalid giftOccasion -> 302 /checkout + flash errorMessage + stock unchanged
        CheckoutPreviewResponse pMvcErr = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", pMvcErr.checkoutToken())
                        .param("shopGifts[" + shop.getId() + "].isGift", "true")
                        .param("shopGifts[" + shop.getId() + "].giftOccasion", "UNKNOWN_OCCASION")
                        .param("shopGifts[" + shop.getId() + "].giftWrapType", "STANDARD_BOX"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("errorMessage"));

        assertEquals(stockBefore, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    @DisplayName("Chi tiết đơn Buyer (/orders & /account/orders) và Seller (/seller/orders): hiển thị quà, ẩn khi không quà, chống XSS")
    void testBuyerAndSellerOrderDetailGiftRenderingAndXssEscaping() throws Exception {
        User seller = createUser("sel_det", Role.SELLER);
        Shop shop = createApprovedShop(seller, "det");
        Product product = createProduct(shop, "cafe", BigDecimal.valueOf(250_000), 20);

        User buyer = createUser("buy_det", Role.USER);
        Address addr = createDefaultAddress(buyer, "det");
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);
        CustomUserDetails sellerDetails = new CustomUserDetails(seller);

        // 1. Gift order with XSS payloads
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse pGift = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        String xssRecipient = "<script>alert('xss-recip')</script>";
        String xssMessage = "Chúc mừng <script>alert('xss')</script> an khang";

        OrderGroup giftGroup = orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                addr.getId(), "COD", "STANDARD", null, null, pGift.checkoutToken(), null,
                Map.of(shop.getId(), new ShopPackageGiftRequest(true, "TET", "BAMBOO_PREMIUM", xssRecipient, xssMessage))
        ), "127.0.0.1");
        entityManager.flush();
        entityManager.clear();

        Order giftOrder = orderRepository.findByOrderGroupId(giftGroup.getId()).get(0);
        String giftCode = giftOrder.getCode();

        // Buyer /orders/{code}
        mockMvc.perform(get("/orders/" + giftCode).with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/orders/detail"))
                .andExpect(content().string(containsString("Thông tin quà biếu")))
                .andExpect(content().string(containsString("Hộp tre đan cao cấp")))
                .andExpect(content().string(containsString("Tết &amp; Lễ hội")))
                .andExpect(content().string(containsString("Phí gói quà:")))
                .andExpect(content().string(containsString("25.000₫")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert('xss')"))));

        // Buyer /account/orders/{code}
        mockMvc.perform(get("/account/orders/" + giftCode).with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/orders/detail"))
                .andExpect(content().string(containsString("Thông tin quà biếu")))
                .andExpect(content().string(not(containsString("<script>alert('xss-recip')"))));

        // Seller /seller/orders/{code}
        mockMvc.perform(get("/seller/orders/" + giftCode).with(user(sellerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/orders/detail"))
                .andExpect(content().string(containsString("Thông tin quà biếu")))
                .andExpect(content().string(containsString("Yêu cầu đóng gói quà")))
                .andExpect(content().string(containsString("Hộp tre đan cao cấp")))
                .andExpect(content().string(containsString("Phí gói quà khách trả:")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(&#39;xss-recip&#39;)&lt;/script&gt;")))
                .andExpect(content().string(not(containsString("<script>alert('xss')"))));

        // 2. Non-gift order -> gift card omitted on buyer & seller detail
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse pPlain = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup plainGroup = orderService.placeOrder(buyer.getId(), new CheckoutFormRequest(
                addr.getId(), "COD", "STANDARD", null, null, pPlain.checkoutToken(), null
        ), "127.0.0.1");
        entityManager.flush();
        entityManager.clear();

        String plainCode = orderRepository.findByOrderGroupId(plainGroup.getId()).get(0).getCode();

        mockMvc.perform(get("/orders/" + plainCode).with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Thông tin quà biếu"))));

        mockMvc.perform(get("/seller/orders/" + plainCode).with(user(sellerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Thông tin quà biếu"))));
    }

    @Test
    @DisplayName("Thiệp chúc mừng nhiều dòng (CRLF): 300 ký tự sau chuẩn hóa thành công, 301 ký tự bị từ chối & giữ nháp đến trang success")
    void testMultiLineGreetingCardCrlfNormalizationAndDraftLifecycle() throws Exception {
        User seller = createUser("sel_crlf", Role.SELLER);
        Shop shop = createApprovedShop(seller, "crlf");
        Product product = createProduct(shop, "mut", BigDecimal.valueOf(180_000), 15);

        User buyer = createUser("buy_crlf", Role.USER);
        Address addr = createDefaultAddress(buyer, "crlf");
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));

        // Verify checkout form does not wipe sessionStorage draft prematurely on @submit
        mockMvc.perform(get("/checkout").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("@submit=\"submitting = true\"")))
                .andExpect(content().string(not(containsString("@submit=\"submitting = true; clearGiftDraft()\""))));

        // 15 segments * 18 chars + 15 chars = 285 chars + 15 "\r\n" => 315 raw chars, 300 normalized chars
        StringBuilder raw300 = new StringBuilder();
        for (int i = 0; i < 15; i++) {
            raw300.append("A".repeat(18)).append("\r\n");
        }
        raw300.append("B".repeat(15));
        assertEquals(315, raw300.length());

        CheckoutPreviewResponse pValid = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        var okResult = mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", pValid.checkoutToken())
                        .param("shopGifts[" + shop.getId() + "].isGift", "true")
                        .param("shopGifts[" + shop.getId() + "].giftOccasion", "FAMILY")
                        .param("shopGifts[" + shop.getId() + "].giftWrapType", "STANDARD_BOX")
                        .param("shopGifts[" + shop.getId() + "].giftRecipientName", "  Bố Mẹ\r\nKính Yêu  ")
                        .param("shopGifts[" + shop.getId() + "].giftMessage", raw300.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/checkout/success?groupCode=")))
                .andReturn();

        String groupCode = okResult.getResponse().getRedirectedUrl().substring("/checkout/success?groupCode=".length());
        entityManager.flush();
        entityManager.clear();

        OrderGroup group = orderGroupRepository.findByCode(groupCode).orElseThrow();
        Order order = orderRepository.findByOrderGroupId(group.getId()).get(0);
        assertEquals("Bố Mẹ Kính Yêu", order.getGiftRecipientName());
        assertNotNull(order.getGiftMessage());
        assertEquals(300, order.getGiftMessage().length());
        assertTrue(order.getGiftMessage().contains("\n"));
        assertFalse(order.getGiftMessage().contains("\r"));

        // Verify order-success clears sessionStorage draft
        mockMvc.perform(get("/checkout/success").param("groupCode", groupCode).with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("web/order-success"))
                .andExpect(content().string(containsString("sessionStorage.removeItem('vs_checkout_gifts')")));

        // 301 normalized chars (286 chars + 15 "\r\n" = 316 raw chars) -> still rejected cleanly without stock decrement
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        int stockBefore = productRepository.findById(product.getId()).orElseThrow().getStock();
        String raw301 = raw300 + "C";
        assertEquals(316, raw301.length());

        CheckoutPreviewResponse pTooLong = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        var errResult = mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", pTooLong.checkoutToken())
                        .param("shopGifts[" + shop.getId() + "].isGift", "true")
                        .param("shopGifts[" + shop.getId() + "].giftOccasion", "FAMILY")
                        .param("shopGifts[" + shop.getId() + "].giftWrapType", "STANDARD_BOX")
                        .param("shopGifts[" + shop.getId() + "].giftMessage", raw301))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andReturn();

        Object errMsg = errResult.getFlashMap().get("errorMessage");
        assertNotNull(errMsg);
        assertTrue(errMsg.toString().contains("300"));
        assertEquals(stockBefore, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    @DisplayName("Tham số binding lỗi (typeMismatch): hiển thị thông báo tiếng Việt sạch, không lộ exception Spring")
    void testMalformedBindingParamShowsCleanVietnameseError() throws Exception {
        User seller = createUser("sel_bind", Role.SELLER);
        Shop shop = createApprovedShop(seller, "bind");
        Product product = createProduct(shop, "keo", BigDecimal.valueOf(120_000), 10);

        User buyer = createUser("buy_bind", Role.USER);
        Address addr = createDefaultAddress(buyer, "bind");
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        int stockBefore = productRepository.findById(product.getId()).orElseThrow().getStock();
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);

        var res = mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(addr.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", preview.checkoutToken())
                        .param("shopGifts[" + shop.getId() + "].isGift", "notabool"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("errorMessage"))
                .andReturn();

        String flashMsg = String.valueOf(res.getFlashMap().get("errorMessage"));
        assertEquals("Vui lòng kiểm tra lại thông tin đặt hàng (địa chỉ, hình thức thanh toán hoặc thông tin quà biếu).", flashMsg);
        assertFalse(flashMsg.contains("Failed to convert property value"));
        assertFalse(flashMsg.contains("java.lang.Boolean"));
        assertEquals(stockBefore, productRepository.findById(product.getId()).orElseThrow().getStock());
    }
}
