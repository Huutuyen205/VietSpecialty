package com.vietspecialty.payment;

import com.vietspecialty.config.AppProperties;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.job.AutoDeliverJob;
import com.vietspecialty.job.PaymentExpiryJob;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.VnpayService;
import com.vietspecialty.util.VnpaySigner;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class VnpayAndScheduledJobsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AppProperties appProperties;

    @Autowired
    private VnpayService vnpayService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CartService cartService;

    @Autowired
    private PaymentExpiryJob paymentExpiryJob;

    @Autowired
    private AutoDeliverJob autoDeliverJob;

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
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private VoucherUsageRepository voucherUsageRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User createBuyerWithAddress(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        User buyer = userRepository.save(User.builder()
                .fullName("Buyer " + prefix)
                .email(prefix + "_" + uid + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0912345678")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
        Province province = provinceRepository.findAll().get(0);
        addressRepository.save(Address.builder()
                .user(buyer)
                .receiverName("Người Nhận " + prefix)
                .receiverPhone("0912345678")
                .province(province)
                .ward("Phường Bến Nghé")
                .streetAddress("12 Lê Lợi")
                .isDefault(true)
                .build());
        return buyer;
    }

    private Product createShopProduct(Shop shop, String prefix, BigDecimal price, int stock) {
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

    private String signParams(Map<String, String> params) {
        String hashData = VnpaySigner.buildHashData(params);
        return VnpaySigner.hmacSHA512(appProperties.getVnpay().getHashSecret(), hashData);
    }

    @Test
    @DisplayName("VnpayServiceImpl.createPaymentUrl tạo đúng vnp_Amount, vnp_TxnRef, chuẩn hóa IPv6 và chữ ký HMAC-SHA512")
    void testCreatePaymentUrlParametersAndSignature() {
        Shop shop = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .findFirst().orElseThrow();
        User buyer = createBuyerWithAddress("vnpurl");
        Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);
        Product product = createShopProduct(shop, "vnpurl", BigDecimal.valueOf(250_000), 10);

        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup group = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD", null, null, preview.checkoutToken(), null),
                "0:0:0:0:0:0:0:1");

        String paymentUrl = vnpayService.createPaymentUrl(group, "0:0:0:0:0:0:0:1");
        assertTrue(paymentUrl.startsWith(appProperties.getVnpay().getPayUrl() + "?"));

        String query = paymentUrl.substring(paymentUrl.indexOf('?') + 1);
        Map<String, String> parsed = new HashMap<>();
        for (String pair : query.split("&")) {
            int idx = pair.indexOf('=');
            String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
            String v = URLDecoder.decode(pair.substring(idx + 1), StandardCharsets.UTF_8);
            parsed.put(k, v);
        }

        String secureHash = parsed.remove("vnp_SecureHash");
        assertNotNull(secureHash);
        assertEquals(String.valueOf(group.getGrandTotal().multiply(BigDecimal.valueOf(100)).longValue()),
                parsed.get("vnp_Amount"));
        assertEquals(group.getCode(), parsed.get("vnp_TxnRef"));
        assertEquals("127.0.0.1", parsed.get("vnp_IpAddr"));

        String expectedHash = signParams(parsed);
        assertEquals(expectedHash, secureHash);
    }

    @Test
    @DisplayName("GET /api/payment/vnpay/ipn và GET /payment/vnpay/return xử lý đủ mã 00, trùng 00, mã lỗi 24, sai chữ ký và thiếu TxnRef")
    void testVnpayIpnAndFailedReturn() throws Exception {
        Shop shop = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .findFirst().orElseThrow();
        User buyer = createBuyerWithAddress("vnpipn");
        Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);
        Product product = createShopProduct(shop, "vnpipn", BigDecimal.valueOf(200_000), 20);

        // Group 1: for IPN success (00) + duplicate idempotency
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse p1 = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup group1 = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD", null, null, p1.checkoutToken(), null),
                "127.0.0.1");
        String amount1 = String.valueOf(group1.getGrandTotal().multiply(BigDecimal.valueOf(100)).longValue());

        Map<String, String> okParams = new HashMap<>();
        okParams.put("vnp_TxnRef", group1.getCode());
        okParams.put("vnp_Amount", amount1);
        okParams.put("vnp_ResponseCode", "00");
        okParams.put("vnp_TransactionNo", "14000001");
        okParams.put("vnp_BankCode", "NCB");
        okParams.put("vnp_PayDate", "20261123120000");
        String okHash = signParams(okParams);

        // 1. Valid IPN 00 -> RspCode=00, OrderGroup=PAID, Order=PENDING_CONFIRM, Payment=SUCCESS
        mockMvc.perform(get("/api/payment/vnpay/ipn")
                        .param("vnp_TxnRef", group1.getCode())
                        .param("vnp_Amount", amount1)
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionNo", "14000001")
                        .param("vnp_BankCode", "NCB")
                        .param("vnp_PayDate", "20261123120000")
                        .param("vnp_SecureHash", okHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        entityManager.flush();
        entityManager.clear();

        OrderGroup paidGroup = orderGroupRepository.findByCode(group1.getCode()).orElseThrow();
        assertEquals(OrderGroupPaymentStatus.PAID, paidGroup.getPaymentStatus());
        Order paidChild = orderRepository.findByOrderGroupId(paidGroup.getId()).get(0);
        assertEquals(OrderStatus.PENDING_CONFIRM, paidChild.getStatus());
        assertEquals(OrderPaymentStatus.PAID, paidChild.getPaymentStatus());
        Payment payment1 = paymentRepository.findTopByOrderGroupIdOrderByCreatedAtDesc(paidGroup.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, payment1.getStatus());
        int historyCountAfterFirst = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(paidChild.getId()).size();

        // 2. Duplicate IPN 00 on already-PAID -> RspCode=02 (Order already confirmed), no duplicate history
        mockMvc.perform(get("/api/payment/vnpay/ipn")
                        .param("vnp_TxnRef", group1.getCode())
                        .param("vnp_Amount", amount1)
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionNo", "14000001")
                        .param("vnp_BankCode", "NCB")
                        .param("vnp_PayDate", "20261123120000")
                        .param("vnp_SecureHash", okHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("02"));

        assertEquals(historyCountAfterFirst,
                orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(paidChild.getId()).size());

        // 3. Invalid signature on IPN -> RspCode=97 (Invalid Checksum)
        mockMvc.perform(get("/api/payment/vnpay/ipn")
                        .param("vnp_TxnRef", group1.getCode())
                        .param("vnp_Amount", amount1)
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "INVALID_HASH_VALUE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("97"));

        // 4. Unknown vnp_TxnRef with valid signature -> RspCode=01 (Order not found)
        Map<String, String> missingParams = new HashMap<>();
        missingParams.put("vnp_TxnRef", "NON_EXISTENT_TXN_999");
        missingParams.put("vnp_Amount", amount1);
        missingParams.put("vnp_ResponseCode", "00");
        String missingHash = signParams(missingParams);

        mockMvc.perform(get("/api/payment/vnpay/ipn")
                        .param("vnp_TxnRef", "NON_EXISTENT_TXN_999")
                        .param("vnp_Amount", amount1)
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", missingHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("01"));

        // Group 2: valid signature with mismatched amount (04), vnp_ResponseCode=24 on IPN (00) and Return URL
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse p2 = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup group2 = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD", null, null, p2.checkoutToken(), null),
                "127.0.0.1");
        String amount2 = String.valueOf(group2.getGrandTotal().multiply(BigDecimal.valueOf(100)).longValue());

        // 4b. IPN with mismatched vnp_Amount -> RspCode=04 (Invalid amount)
        Map<String, String> mismatchParams = new HashMap<>();
        mismatchParams.put("vnp_TxnRef", group2.getCode());
        mismatchParams.put("vnp_Amount", "100");
        mismatchParams.put("vnp_ResponseCode", "00");
        String mismatchHash = signParams(mismatchParams);

        mockMvc.perform(get("/api/payment/vnpay/ipn")
                        .param("vnp_TxnRef", group2.getCode())
                        .param("vnp_Amount", "100")
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", mismatchHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("04"));
        assertEquals(OrderGroupPaymentStatus.UNPAID,
                orderGroupRepository.findByCode(group2.getCode()).orElseThrow().getPaymentStatus());

        Map<String, String> cancelIpnParams = new HashMap<>();
        cancelIpnParams.put("vnp_TxnRef", group2.getCode());
        cancelIpnParams.put("vnp_Amount", amount2);
        cancelIpnParams.put("vnp_ResponseCode", "24");
        String cancelIpnHash = signParams(cancelIpnParams);

        // 5. IPN with vnp_ResponseCode=24 and valid vnp_Amount -> RspCode=00, OrderGroup=FAILED
        mockMvc.perform(get("/api/payment/vnpay/ipn")
                        .param("vnp_TxnRef", group2.getCode())
                        .param("vnp_Amount", amount2)
                        .param("vnp_ResponseCode", "24")
                        .param("vnp_SecureHash", cancelIpnHash))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.RspCode").value("00"));

        entityManager.flush();
        entityManager.clear();
        assertEquals(OrderGroupPaymentStatus.FAILED,
                orderGroupRepository.findByCode(group2.getCode()).orElseThrow().getPaymentStatus());

        // 6. Return URL with vnp_ResponseCode=24 (no vnp_Amount) and valid signature -> 200 web/payment-failed
        Map<String, String> cancelReturnParams = new HashMap<>();
        cancelReturnParams.put("vnp_TxnRef", group2.getCode());
        cancelReturnParams.put("vnp_ResponseCode", "24");
        String cancelReturnHash = signParams(cancelReturnParams);

        mockMvc.perform(get("/payment/vnpay/return")
                        .param("vnp_TxnRef", group2.getCode())
                        .param("vnp_ResponseCode", "24")
                        .param("vnp_SecureHash", cancelReturnHash))
                .andExpect(status().isOk())
                .andExpect(view().name("web/payment-failed"))
                .andExpect(model().attribute("responseCode", "24"));
    }

    @Test
    @DisplayName("PaymentExpiryJob tự động hết hạn đơn VNPay quá 15 phút, hủy đơn, hoàn kho và hoàn lượt voucher")
    void testPaymentExpiryJobRestoresStockAndVouchers() {
        Shop shop = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .findFirst().orElseThrow();
        User admin = userRepository.findByEmailIgnoreCase("admin@vietspecialty.vn").orElseThrow();
        User buyer = createBuyerWithAddress("exp");
        Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);

        int initialStock = 15;
        Product product = createShopProduct(shop, "exp", BigDecimal.valueOf(200_000), initialStock);
        String uid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Voucher shopVoucher = voucherRepository.save(Voucher.builder()
                .code("EXPSH_" + uid)
                .name("Expiry Shop Voucher")
                .scope(VoucherScope.SHOP)
                .shop(shop)
                .discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(15_000))
                .minOrderValue(BigDecimal.ZERO)
                .usageLimit(10)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .createdBy(admin)
                .build());

        Voucher platformVoucher = voucherRepository.save(Voucher.builder()
                .code("EXPPL_" + uid)
                .name("Expiry Platform Voucher")
                .scope(VoucherScope.PLATFORM)
                .discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(20_000))
                .minOrderValue(BigDecimal.ZERO)
                .usageLimit(10)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .createdBy(admin)
                .build());

        // Order 1: expired VNPAY with shop + platform vouchers
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 3));
        CheckoutPreviewResponse pExpired = orderService.previewCheckout(buyer.getId(), "STANDARD",
                platformVoucher.getCode(), Map.of(shop.getId(), shopVoucher.getCode()));
        OrderGroup expiredGroup = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD",
                        platformVoucher.getCode(), Map.of(shop.getId(), shopVoucher.getCode()),
                        pExpired.checkoutToken(), null),
                "127.0.0.1");

        // Order 2: active non-expired VNPAY
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 2));
        CheckoutPreviewResponse pActive = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup activeGroup = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD",
                        null, null, pActive.checkoutToken(), null),
                "127.0.0.1");

        entityManager.flush();
        entityManager.clear();

        // Stock should now be 15 - 3 - 2 = 10, vouchers usedCount = 1
        assertEquals(10, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(1, voucherRepository.findById(shopVoucher.getId()).orElseThrow().getUsedCount());
        assertEquals(1, voucherRepository.findById(platformVoucher.getId()).orElseThrow().getUsedCount());

        // Backdate expiredGroup.paymentExpiresAt
        OrderGroup toBackdate = orderGroupRepository.findById(expiredGroup.getId()).orElseThrow();
        toBackdate.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(20));
        orderGroupRepository.save(toBackdate);
        entityManager.flush();
        entityManager.clear();

        // Run PaymentExpiryJob
        paymentExpiryJob.run();
        entityManager.flush();
        entityManager.clear();

        OrderGroup reloadedExpired = orderGroupRepository.findById(expiredGroup.getId()).orElseThrow();
        assertEquals(OrderGroupPaymentStatus.EXPIRED, reloadedExpired.getPaymentStatus());

        Order reloadedOrder = orderRepository.findByOrderGroupId(expiredGroup.getId()).get(0);
        assertEquals(OrderStatus.CANCELLED, reloadedOrder.getStatus());
        assertEquals(ActorType.SYSTEM, reloadedOrder.getCancelledBy());

        // Stock restored (+3 -> 13) and both vouchers released back to 0
        assertEquals(13, productRepository.findById(product.getId()).orElseThrow().getStock());
        assertEquals(0, voucherRepository.findById(shopVoucher.getId()).orElseThrow().getUsedCount());
        assertEquals(0, voucherRepository.findById(platformVoucher.getId()).orElseThrow().getUsedCount());

        // Non-expired VNPAY group stays UNPAID
        OrderGroup reloadedActive = orderGroupRepository.findById(activeGroup.getId()).orElseThrow();
        assertEquals(OrderGroupPaymentStatus.UNPAID, reloadedActive.getPaymentStatus());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-01 (Non-Transactional): PaymentExpiryJob xử lý sạch OrderGroup nhiều gian hàng (2+ orders) và nhiều OrderGroup quá hạn không bị LazyInitializationException")
    void testPaymentExpiryJobMultiShopAndMultiGroupNonTransactional() {
        User buyer = null;
        User seller1 = null;
        User seller2 = null;
        Shop shop1 = null;
        Shop shop2 = null;
        Product prod1 = null;
        Product prod2 = null;
        List<Voucher> createdVouchers = new ArrayList<>();
        List<OrderGroup> createdGroups = new ArrayList<>();
        try {
            User admin = userRepository.findByEmailIgnoreCase("admin@vietspecialty.vn").orElseThrow();
            Province province = provinceRepository.findAll().get(0);
            String uid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

            buyer = createBuyerWithAddress("expmulti");
            Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);

            seller1 = userRepository.save(User.builder()
                    .fullName("Seller Exp1 " + uid)
                    .email("sel_exp1_" + uid.toLowerCase() + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@"))
                    .phone("0912345671")
                    .role(Role.SELLER)
                    .status(UserStatus.ACTIVE)
                    .build());
            seller2 = userRepository.save(User.builder()
                    .fullName("Seller Exp2 " + uid)
                    .email("sel_exp2_" + uid.toLowerCase() + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@"))
                    .phone("0912345672")
                    .role(Role.SELLER)
                    .status(UserStatus.ACTIVE)
                    .build());

            shop1 = shopRepository.save(Shop.builder()
                    .name("Shop Exp 1 " + uid)
                    .slug("shop-exp-1-" + uid.toLowerCase())
                    .owner(seller1)
                    .province(province)
                    .addressDetail("11 Đường Test 1")
                    .phone("0912345671")
                    .status(ShopStatus.APPROVED)
                    .build());
            shop2 = shopRepository.save(Shop.builder()
                    .name("Shop Exp 2 " + uid)
                    .slug("shop-exp-2-" + uid.toLowerCase())
                    .owner(seller2)
                    .province(province)
                    .addressDetail("22 Đường Test 2")
                    .phone("0912345672")
                    .status(ShopStatus.APPROVED)
                    .build());

            prod1 = createShopProduct(shop1, "expm1", BigDecimal.valueOf(200_000), 15);
            prod2 = createShopProduct(shop2, "expm2", BigDecimal.valueOf(150_000), 12);

            Voucher shopVoucher1 = voucherRepository.save(Voucher.builder()
                    .code("EXPM1_" + uid)
                    .name("Multi Expiry Shop1 Voucher")
                    .scope(VoucherScope.SHOP)
                    .shop(shop1)
                    .discountType(DiscountType.FIXED)
                    .discountValue(BigDecimal.valueOf(15_000))
                    .minOrderValue(BigDecimal.ZERO)
                    .usageLimit(10)
                    .perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true)
                    .createdBy(admin)
                    .build());
            createdVouchers.add(shopVoucher1);

            Voucher shopVoucher2 = voucherRepository.save(Voucher.builder()
                    .code("EXPM2_" + uid)
                    .name("Multi Expiry Shop2 Voucher")
                    .scope(VoucherScope.SHOP)
                    .shop(shop2)
                    .discountType(DiscountType.FIXED)
                    .discountValue(BigDecimal.valueOf(10_000))
                    .minOrderValue(BigDecimal.ZERO)
                    .usageLimit(10)
                    .perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true)
                    .createdBy(admin)
                    .build());
            createdVouchers.add(shopVoucher2);

            Voucher platformVoucher = voucherRepository.save(Voucher.builder()
                    .code("EXPMPL_" + uid)
                    .name("Multi Expiry Platform Voucher")
                    .scope(VoucherScope.PLATFORM)
                    .discountType(DiscountType.FIXED)
                    .discountValue(BigDecimal.valueOf(25_000))
                    .minOrderValue(BigDecimal.ZERO)
                    .usageLimit(10)
                    .perUserLimit(2)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true)
                    .createdBy(admin)
                    .build());
            createdVouchers.add(platformVoucher);

            // Group 1: 2-shop expired VNPAY OrderGroup with 2 shop vouchers + 1 platform voucher
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod1.getId(), 3));
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod2.getId(), 2));
            Map<Long, String> shopCodes = Map.of(
                    shop1.getId(), shopVoucher1.getCode(),
                    shop2.getId(), shopVoucher2.getCode()
            );
            CheckoutPreviewResponse pMulti = orderService.previewCheckout(
                    buyer.getId(), "STANDARD", platformVoucher.getCode(), shopCodes);
            OrderGroup expiredMultiGroup = orderService.placeOrder(buyer.getId(),
                    new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD",
                            platformVoucher.getCode(), shopCodes, pMulti.checkoutToken(), null),
                    "127.0.0.1");
            createdGroups.add(expiredMultiGroup);

            // Group 2: 2nd expired VNPAY OrderGroup in the same scheduled batch
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod1.getId(), 1));
            CheckoutPreviewResponse pSecond = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
            OrderGroup expiredSecondGroup = orderService.placeOrder(buyer.getId(),
                    new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD",
                            null, null, pSecond.checkoutToken(), null),
                    "127.0.0.1");
            createdGroups.add(expiredSecondGroup);

            // Group 3: Active non-expired VNPAY OrderGroup (must stay UNPAID)
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod2.getId(), 1));
            CheckoutPreviewResponse pActive = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
            OrderGroup activeGroup = orderService.placeOrder(buyer.getId(),
                    new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD",
                            null, null, pActive.checkoutToken(), null),
                    "127.0.0.1");
            createdGroups.add(activeGroup);

            assertEquals(11, productRepository.findById(prod1.getId()).orElseThrow().getStock());
            assertEquals(9, productRepository.findById(prod2.getId()).orElseThrow().getStock());
            assertEquals(1, voucherRepository.findById(shopVoucher1.getId()).orElseThrow().getUsedCount());
            assertEquals(1, voucherRepository.findById(shopVoucher2.getId()).orElseThrow().getUsedCount());
            assertEquals(1, voucherRepository.findById(platformVoucher.getId()).orElseThrow().getUsedCount());

            // Backdate both expired groups
            OrderGroup g1 = orderGroupRepository.findById(expiredMultiGroup.getId()).orElseThrow();
            g1.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(25));
            orderGroupRepository.save(g1);

            OrderGroup g2 = orderGroupRepository.findById(expiredSecondGroup.getId()).orElseThrow();
            g2.setPaymentExpiresAt(LocalDateTime.now().minusMinutes(20));
            orderGroupRepository.save(g2);

            // Run PaymentExpiryJob in its own real transaction (NOT_SUPPORTED test)
            paymentExpiryJob.run();

            OrderGroup reloadedG1 = orderGroupRepository.findById(expiredMultiGroup.getId()).orElseThrow();
            assertEquals(OrderGroupPaymentStatus.EXPIRED, reloadedG1.getPaymentStatus());
            OrderGroup reloadedG2 = orderGroupRepository.findById(expiredSecondGroup.getId()).orElseThrow();
            assertEquals(OrderGroupPaymentStatus.EXPIRED, reloadedG2.getPaymentStatus());

            List<Order> g1Orders = orderRepository.findByOrderGroupId(expiredMultiGroup.getId());
            assertEquals(2, g1Orders.size());
            for (Order o : g1Orders) {
                assertEquals(OrderStatus.CANCELLED, o.getStatus());
                assertEquals(ActorType.SYSTEM, o.getCancelledBy());
                List<OrderStatusHistory> histories = orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(o.getId());
                assertTrue(histories.stream().anyMatch(h ->
                        h.getFromStatus() == OrderStatus.PENDING_PAYMENT && h.getToStatus() == OrderStatus.CANCELLED));
            }

            List<Order> g2Orders = orderRepository.findByOrderGroupId(expiredSecondGroup.getId());
            assertEquals(1, g2Orders.size());
            assertEquals(OrderStatus.CANCELLED, g2Orders.get(0).getStatus());
            assertEquals(ActorType.SYSTEM, g2Orders.get(0).getCancelledBy());

            // Stock restored for both shops' products (prod1: 11 + 3 + 1 = 15, prod2: 9 + 2 = 11)
            assertEquals(15, productRepository.findById(prod1.getId()).orElseThrow().getStock());
            assertEquals(11, productRepository.findById(prod2.getId()).orElseThrow().getStock());

            // Vouchers & VoucherUsage rows released
            assertEquals(0, voucherRepository.findById(shopVoucher1.getId()).orElseThrow().getUsedCount());
            assertEquals(0, voucherRepository.findById(shopVoucher2.getId()).orElseThrow().getUsedCount());
            assertEquals(0, voucherRepository.findById(platformVoucher.getId()).orElseThrow().getUsedCount());

            List<VoucherUsage> g1Usages = voucherUsageRepository.findByOrderGroupId(expiredMultiGroup.getId());
            assertEquals(3, g1Usages.size());
            assertTrue(g1Usages.stream().allMatch(vu -> vu.getStatus() == VoucherUsageStatus.RELEASED));

            // Active non-expired group stays UNPAID / PENDING_PAYMENT
            OrderGroup reloadedActive = orderGroupRepository.findById(activeGroup.getId()).orElseThrow();
            assertEquals(OrderGroupPaymentStatus.UNPAID, reloadedActive.getPaymentStatus());
            assertEquals(OrderStatus.PENDING_PAYMENT,
                    orderRepository.findByOrderGroupId(activeGroup.getId()).get(0).getStatus());
        } finally {
            for (User u : Arrays.asList(buyer, seller1, seller2)) {
                if (u != null && u.getId() != null) {
                    notificationRepository.deleteAll(
                            notificationRepository.findByUserIdOrderByCreatedAtDesc(u.getId(), Pageable.unpaged()).getContent());
                }
            }
            for (OrderGroup g : createdGroups) {
                voucherUsageRepository.deleteAll(voucherUsageRepository.findByOrderGroupId(g.getId()));
                paymentRepository.deleteAll(paymentRepository.findByOrderGroupIdOrderByCreatedAtDesc(g.getId()));
                orderRepository.deleteAll(orderRepository.findByOrderGroupId(g.getId()));
                orderGroupRepository.deleteById(g.getId());
            }
            if (buyer != null && buyer.getId() != null) {
                cartItemRepository.deleteAll(cartItemRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId()));
            }
            for (Voucher v : createdVouchers) {
                if (v != null && v.getId() != null) {
                    voucherRepository.deleteById(v.getId());
                }
            }
            for (Product p : Arrays.asList(prod1, prod2)) {
                if (p != null && p.getId() != null) {
                    productRepository.deleteById(p.getId());
                }
            }
            if (buyer != null && buyer.getId() != null) {
                addressRepository.deleteAll(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()));
            }
            for (Shop s : Arrays.asList(shop1, shop2)) {
                if (s != null && s.getId() != null) {
                    shopRepository.deleteById(s.getId());
                }
            }
            for (User u : Arrays.asList(buyer, seller1, seller2)) {
                if (u != null && u.getId() != null) {
                    userRepository.deleteById(u.getId());
                }
            }
        }
    }

    @Test
    @DisplayName("AutoDeliverJob tự động chuyển SHIPPING quá 7 ngày sang DELIVERED, PAID, tăng soldCount và tạo thông báo")
    void testAutoDeliverJob() {
        Shop shop = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .findFirst().orElseThrow();
        User buyer = createBuyerWithAddress("autod");
        Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);
        Product product = createShopProduct(shop, "autod", BigDecimal.valueOf(180_000), 20);

        // Order 1: shipped 8 days ago -> should auto-deliver
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 2));
        CheckoutPreviewResponse p1 = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup g1 = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(address.getId(), "COD", "STANDARD", null, null, p1.checkoutToken(), null),
                "127.0.0.1");
        Order orderDue = orderRepository.findByOrderGroupId(g1.getId()).get(0);
        orderService.confirmSellerOrder(orderDue.getId(), shop.getId());
        orderService.shipSellerOrder(orderDue.getId(), shop.getId(), "GHTK", "GHTK111");

        // Order 2: shipped 2 days ago -> must stay SHIPPING
        cartService.addToCart(buyer.getId(), new CartItemRequest(product.getId(), 1));
        CheckoutPreviewResponse p2 = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        OrderGroup g2 = orderService.placeOrder(buyer.getId(),
                new CheckoutFormRequest(address.getId(), "COD", "STANDARD", null, null, p2.checkoutToken(), null),
                "127.0.0.1");
        Order orderRecent = orderRepository.findByOrderGroupId(g2.getId()).get(0);
        orderService.confirmSellerOrder(orderRecent.getId(), shop.getId());
        orderService.shipSellerOrder(orderRecent.getId(), shop.getId(), "GHTK", "GHTK222");

        entityManager.flush();
        entityManager.clear();

        Order dueEntity = orderRepository.findById(orderDue.getId()).orElseThrow();
        dueEntity.setShippedAt(LocalDateTime.now().minusDays(8));
        orderRepository.save(dueEntity);

        Order recentEntity = orderRepository.findById(orderRecent.getId()).orElseThrow();
        recentEntity.setShippedAt(LocalDateTime.now().minusDays(2));
        orderRepository.save(recentEntity);

        entityManager.flush();
        entityManager.clear();

        // Run AutoDeliverJob
        autoDeliverJob.run();
        entityManager.flush();
        entityManager.clear();

        Order delivered = orderRepository.findById(orderDue.getId()).orElseThrow();
        assertEquals(OrderStatus.DELIVERED, delivered.getStatus());
        assertEquals(OrderPaymentStatus.PAID, delivered.getPaymentStatus());
        assertNotNull(delivered.getDeliveredAt());

        Product updatedProduct = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(2, updatedProduct.getSoldCount());

        List<Notification> buyerNotifs = notificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(buyer.getId());
        assertTrue(buyerNotifs.stream().anyMatch(n -> "ORDER_DELIVERED".equals(n.getType())));

        if (shop.getOwner() != null) {
            List<Notification> sellerNotifs = notificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(shop.getOwner().getId());
            assertTrue(sellerNotifs.stream().anyMatch(n -> "ORDER_COMPLETED".equals(n.getType())));
        }

        Order stillShipping = orderRepository.findById(orderRecent.getId()).orElseThrow();
        assertEquals(OrderStatus.SHIPPING, stillShipping.getStatus());
    }
}
