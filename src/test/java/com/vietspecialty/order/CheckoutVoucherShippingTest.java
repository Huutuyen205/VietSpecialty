package com.vietspecialty.order;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.request.CheckoutFormRequest;
import com.vietspecialty.dto.response.CheckoutPreviewResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.VoucherService;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
class CheckoutVoucherShippingTest {

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
    private VoucherUsageRepository voucherUsageRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private VoucherService voucherService;

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

    @Test
    @DisplayName("Quy tắc phí ship: <500k STANDARD (30k), >=500k STANDARD (0đ), >=500k EXPRESS (50k), và tách đa shop")
    void testShippingThresholdRules() {
        List<Shop> approvedShops = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .toList();
        assertTrue(approvedShops.size() >= 2);
        Shop shopA = approvedShops.get(0);
        Shop shopB = approvedShops.get(1);

        User buyer = createBuyerWithAddress("ship");
        Product pLow = createShopProduct(shopA, "low", BigDecimal.valueOf(200_000), 20);
        Product pHigh = createShopProduct(shopA, "high", BigDecimal.valueOf(500_000), 20);
        Product pShopB = createShopProduct(shopB, "shopb", BigDecimal.valueOf(150_000), 20);

        // 1. < 500,000đ STANDARD -> 30,000đ
        cartService.addToCart(buyer.getId(), new CartItemRequest(pLow.getId(), 1));
        CheckoutPreviewResponse under500 = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        assertEquals(0, BigDecimal.valueOf(30_000).compareTo(under500.totalShippingFee()));

        // 2. >= 500,000đ STANDARD -> 0đ
        cartService.clearCart(buyer.getId());
        cartService.addToCart(buyer.getId(), new CartItemRequest(pHigh.getId(), 1));
        CheckoutPreviewResponse at500Std = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        assertEquals(0, BigDecimal.ZERO.compareTo(at500Std.totalShippingFee()));

        // 3. >= 500,000đ EXPRESS -> 50,000đ
        CheckoutPreviewResponse at500Exp = orderService.previewCheckout(buyer.getId(), "EXPRESS", null, null);
        assertEquals(0, BigDecimal.valueOf(50_000).compareTo(at500Exp.totalShippingFee()));

        // 4. Multi-shop split: Shop A >= 500k (0đ) + Shop B < 500k (30k) -> 30,000đ total shipping
        cartService.addToCart(buyer.getId(), new CartItemRequest(pShopB.getId(), 1));
        CheckoutPreviewResponse multiShop = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        assertEquals(0, BigDecimal.valueOf(30_000).compareTo(multiShop.totalShippingFee()));
        assertEquals(2, multiShop.shopGroups().size());
    }

    @Test
    @DisplayName("Shop Voucher: PERCENT có maxDiscount, FIXED, validation lỗi (minOrderValue, mismatch, expired, usage exceeded) và tăng usedCount")
    void testShopVoucherValidationAndApplication() throws Exception {
        List<Shop> approvedShops = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .toList();
        Shop shopA = approvedShops.get(0);
        Shop shopB = approvedShops.get(1);
        User admin = userRepository.findByEmailIgnoreCase("admin@vietspecialty.vn").orElseThrow();
        User buyer = createBuyerWithAddress("sv");
        Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        Product prodA = createShopProduct(shopA, "sva", BigDecimal.valueOf(300_000), 20);
        String uid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // PERCENT 20% maxDiscount 40,000đ on 300,000đ (raw 60,000đ -> capped at 40,000đ)
        Voucher percentVoucher = voucherRepository.save(Voucher.builder()
                .code("SHPCT_" + uid)
                .name("Shop Percent Capped")
                .scope(VoucherScope.SHOP)
                .shop(shopA)
                .discountType(DiscountType.PERCENT)
                .discountValue(BigDecimal.valueOf(20))
                .maxDiscount(BigDecimal.valueOf(40_000))
                .minOrderValue(BigDecimal.valueOf(200_000))
                .usageLimit(5)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .createdBy(admin)
                .build());

        // FIXED 25,000đ
        Voucher fixedVoucher = voucherRepository.save(Voucher.builder()
                .code("SHFIX_" + uid)
                .name("Shop Fixed")
                .scope(VoucherScope.SHOP)
                .shop(shopA)
                .discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(25_000))
                .minOrderValue(BigDecimal.valueOf(100_000))
                .usageLimit(5)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .createdBy(admin)
                .build());

        // High minOrderValue voucher
        Voucher minOrderVoucher = voucherRepository.save(Voucher.builder()
                .code("SHMIN_" + uid)
                .name("High Min Order")
                .scope(VoucherScope.SHOP)
                .shop(shopA)
                .discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(50_000))
                .minOrderValue(BigDecimal.valueOf(1_000_000))
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .createdBy(admin)
                .build());

        // Expired voucher
        Voucher expiredVoucher = voucherRepository.save(Voucher.builder()
                .code("SHEXP_" + uid)
                .name("Expired")
                .scope(VoucherScope.SHOP)
                .shop(shopA)
                .discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(10_000))
                .minOrderValue(BigDecimal.ZERO)
                .startAt(LocalDateTime.now().minusDays(10))
                .endAt(LocalDateTime.now().minusDays(1))
                .isActive(true)
                .createdBy(admin)
                .build());

        // Usage exceeded voucher
        Voucher exceededVoucher = voucherRepository.save(Voucher.builder()
                .code("SHFULL_" + uid)
                .name("Full Usage")
                .scope(VoucherScope.SHOP)
                .shop(shopA)
                .discountType(DiscountType.FIXED)
                .discountValue(BigDecimal.valueOf(10_000))
                .minOrderValue(BigDecimal.ZERO)
                .usageLimit(1)
                .usedCount(1)
                .startAt(LocalDateTime.now().minusDays(1))
                .endAt(LocalDateTime.now().plusDays(5))
                .isActive(true)
                .createdBy(admin)
                .build());

        // Validation error checks at service layer
        BusinessException minEx = assertThrows(BusinessException.class, () ->
                voucherService.validateAndGetVoucher(minOrderVoucher.getCode(), shopA.getId(), BigDecimal.valueOf(300_000), buyer.getId()));
        assertEquals(ErrorCode.VOUCHER_MIN_ORDER_NOT_MET, minEx.getErrorCode());

        BusinessException mismatchEx = assertThrows(BusinessException.class, () ->
                voucherService.validateAndGetVoucher(percentVoucher.getCode(), shopB.getId(), BigDecimal.valueOf(300_000), buyer.getId()));
        assertEquals(ErrorCode.VOUCHER_SHOP_MISMATCH, mismatchEx.getErrorCode());

        BusinessException expEx = assertThrows(BusinessException.class, () ->
                voucherService.validateAndGetVoucher(expiredVoucher.getCode(), shopA.getId(), BigDecimal.valueOf(300_000), buyer.getId()));
        assertEquals(ErrorCode.VOUCHER_EXPIRED, expEx.getErrorCode());

        BusinessException usageEx = assertThrows(BusinessException.class, () ->
                voucherService.validateAndGetVoucher(exceededVoucher.getCode(), shopA.getId(), BigDecimal.valueOf(300_000), buyer.getId()));
        assertEquals(ErrorCode.VOUCHER_USAGE_EXCEEDED, usageEx.getErrorCode());

        // Fixed discount check
        assertEquals(0, BigDecimal.valueOf(25_000).compareTo(
                voucherService.calculateDiscount(fixedVoucher, BigDecimal.valueOf(300_000))));

        // Preview via GET /checkout?shopVoucher_{id}=...
        cartService.addToCart(buyer.getId(), new CartItemRequest(prodA.getId(), 1));
        var mvcResult = mockMvc.perform(get("/checkout")
                        .with(user(buyerDetails))
                        .param("shippingMethod", "STANDARD")
                        .param("shopVoucher_" + shopA.getId(), percentVoucher.getCode()))
                .andExpect(status().isOk())
                .andExpect(view().name("web/checkout"))
                .andReturn();

        CheckoutPreviewResponse preview = (CheckoutPreviewResponse) mvcResult.getModelAndView().getModel().get("preview");
        assertNotNull(preview);
        assertEquals(0, BigDecimal.valueOf(40_000).compareTo(preview.totalShopDiscount()));
        // 300,000 + 30,000 ship - 40,000 shopDiscount = 290,000
        assertEquals(0, BigDecimal.valueOf(290_000).compareTo(preview.grandTotal()));

        // Place order via POST /checkout/place-order with shopVoucherCodes[{id}]
        mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("shopVoucherCodes[" + shopA.getId() + "]", percentVoucher.getCode())
                        .param("checkoutToken", preview.checkoutToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/checkout/success?groupCode=")));

        entityManager.flush();
        entityManager.clear();

        Voucher reloaded = voucherRepository.findById(percentVoucher.getId()).orElseThrow();
        assertEquals(1, reloaded.getUsedCount(), "Voucher.usedCount phải tăng lên 1 sau khi đặt hàng thành công");
    }

    @Test
    @DisplayName("POST /checkout/place-order: thiếu addressId, COD thành công, VNPAY redirect sandbox và chặn double-submit token")
    void testPlaceOrderWebEndpointFlows() throws Exception {
        Shop shopA = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .findFirst().orElseThrow();
        User buyer = createBuyerWithAddress("po");
        Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);
        Product prod = createShopProduct(shopA, "po", BigDecimal.valueOf(120_000), 20);

        cartService.addToCart(buyer.getId(), new CartItemRequest(prod.getId(), 1));
        CheckoutPreviewResponse previewCod = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);

        // 1. Missing addressId -> 302 /checkout + flash errorMessage
        mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", previewCod.checkoutToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("errorMessage"));

        // 2. COD -> 302 /checkout/success?groupCode=...
        mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", previewCod.checkoutToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("/checkout/success?groupCode=")));

        // 3. Reused checkoutToken -> 302 /checkout + flash errorMessage
        cartService.addToCart(buyer.getId(), new CartItemRequest(prod.getId(), 1));
        mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", "COD")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", previewCod.checkoutToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout"))
                .andExpect(flash().attributeExists("errorMessage"));

        // 4. VNPAY -> 302 to sandbox.vnpayment.vn
        CheckoutPreviewResponse previewVnpay = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
        mockMvc.perform(post("/checkout/place-order")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("addressId", String.valueOf(address.getId()))
                        .param("paymentMethod", "VNPAY")
                        .param("shippingMethod", "STANDARD")
                        .param("checkoutToken", previewVnpay.checkoutToken()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://sandbox.vnpayment.vn/")));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-01 & DEF-09 (Non-Transactional): Đặt hàng COD dưới open-in-view=false không lỗi LazyInitializationException, không double-validate voucher, không tạo Payment VNPAY")
    void testPlaceOrderNonTransactionalDetachmentAndCodPaymentGuard() throws Exception {
        User buyer = null;
        User seller = null;
        Shop shop = null;
        Product prod = null;
        Voucher singleUseShopVoucher = null;
        List<OrderGroup> createdGroups = new ArrayList<>();
        try {
            String uid = UUID.randomUUID().toString().substring(0, 8);
            Province province = provinceRepository.findAll().get(0);
            User admin = userRepository.findByEmailIgnoreCase("admin@vietspecialty.vn").orElseThrow();

            seller = userRepository.save(User.builder()
                    .fullName("Seller DEF01 " + uid)
                    .email("sel_def01_" + uid + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@"))
                    .phone("0912345678")
                    .role(Role.SELLER)
                    .status(UserStatus.ACTIVE)
                    .build());
            shop = shopRepository.save(Shop.builder()
                    .owner(seller)
                    .name("Shop DEF01 " + uid)
                    .slug("shop-def01-" + uid)
                    .province(province)
                    .addressDetail("12 Đường Test")
                    .phone("0912345678")
                    .status(ShopStatus.APPROVED)
                    .build());
            buyer = createBuyerWithAddress("def01");
            Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);
            CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

            prod = createShopProduct(shop, "def01", BigDecimal.valueOf(250_000), 20);
            singleUseShopVoucher = voucherRepository.save(Voucher.builder()
                    .code("ONEUSE_" + uid.toUpperCase())
                    .name("One Use Shop Voucher")
                    .scope(VoucherScope.SHOP)
                    .shop(shop)
                    .discountType(DiscountType.FIXED)
                    .discountValue(BigDecimal.valueOf(20_000))
                    .minOrderValue(BigDecimal.ZERO)
                    .usageLimit(1)
                    .perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(1))
                    .endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true)
                    .createdBy(admin)
                    .build());

            // 1. COD order with usageLimit=1 shop voucher (proves no double validation & no LazyInitializationException)
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod.getId(), 1));
            CheckoutPreviewResponse previewCod = orderService.previewCheckout(buyer.getId(), "STANDARD",
                    null, Map.of(shop.getId(), singleUseShopVoucher.getCode()));

            var codResult = mockMvc.perform(post("/checkout/place-order")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .param("addressId", String.valueOf(address.getId()))
                            .param("paymentMethod", "COD")
                            .param("shippingMethod", "STANDARD")
                            .param("shopVoucherCodes[" + shop.getId() + "]", singleUseShopVoucher.getCode())
                            .param("checkoutToken", previewCod.checkoutToken()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", startsWith("/checkout/success?groupCode=")))
                    .andReturn();

            String codLocation = codResult.getResponse().getRedirectedUrl();
            String codGroupCode = codLocation.substring(codLocation.indexOf("groupCode=") + "groupCode=".length());
            OrderGroup codGroup = orderGroupRepository.findByCode(codGroupCode).orElseThrow();
            createdGroups.add(codGroup);

            // DEF-09: COD must NOT create Payment row
            assertTrue(paymentRepository.findByOrderGroupIdOrderByCreatedAtDesc(codGroup.getId()).isEmpty(),
                    "Đơn COD không được tạo bản ghi Payment VNPAY");

            List<Order> codOrders = orderRepository.findByOrderGroupId(codGroup.getId());
            assertEquals(1, codOrders.size());
            assertTrue(codOrders.get(0).getShippingAddress().contains(province.getName()));
            assertEquals(1, voucherRepository.findById(singleUseShopVoucher.getId()).orElseThrow().getUsedCount());
            assertEquals(1, voucherUsageRepository.findByOrderId(codOrders.get(0).getId()).size());
            assertFalse(notificationRepository.findByUserIdOrderByCreatedAtDesc(seller.getId(), Pageable.unpaged()).isEmpty());

            // 2. VNPAY order MUST create Payment(VNPAY, PENDING)
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod.getId(), 1));
            CheckoutPreviewResponse previewVnpay = orderService.previewCheckout(buyer.getId(), "STANDARD", null, null);
            OrderGroup vnpayGroup = orderService.placeOrder(buyer.getId(),
                    new CheckoutFormRequest(address.getId(), "VNPAY", "STANDARD", null, null, previewVnpay.checkoutToken(), null),
                    "127.0.0.1");
            createdGroups.add(vnpayGroup);

            List<Payment> vnpayPayments = paymentRepository.findByOrderGroupIdOrderByCreatedAtDesc(vnpayGroup.getId());
            assertEquals(1, vnpayPayments.size());
            assertEquals(PaymentMethod.VNPAY, vnpayPayments.get(0).getMethod());
            assertEquals(PaymentStatus.PENDING, vnpayPayments.get(0).getStatus());
        } finally {
            if (buyer != null && buyer.getId() != null) {
                notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(buyer.getId(), Pageable.unpaged()).getContent());
            }
            if (seller != null && seller.getId() != null) {
                notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(seller.getId(), Pageable.unpaged()).getContent());
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
            if (singleUseShopVoucher != null && singleUseShopVoucher.getId() != null) {
                voucherRepository.deleteById(singleUseShopVoucher.getId());
            }
            if (prod != null && prod.getId() != null) {
                productRepository.deleteById(prod.getId());
            }
            if (buyer != null && buyer.getId() != null) {
                addressRepository.deleteAll(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()));
            }
            if (shop != null && shop.getId() != null) {
                shopRepository.deleteById(shop.getId());
            }
            if (buyer != null && buyer.getId() != null) {
                userRepository.deleteById(buyer.getId());
            }
            if (seller != null && seller.getId() != null) {
                userRepository.deleteById(seller.getId());
            }
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-08 & DEF-13 (Non-Transactional): Lọc voucher gợi ý hợp lệ, kiểm tra perUserLimit và chỉ release voucher sàn khi tất cả đơn con đã hủy")
    void testVoucherPerUserLimitMultiShopReleaseAndAvailabilityFilter() {
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
            String uid = UUID.randomUUID().toString().substring(0, 8);
            Province province = provinceRepository.findAll().get(0);
            User admin = userRepository.findByEmailIgnoreCase("admin@vietspecialty.vn").orElseThrow();

            seller1 = userRepository.save(User.builder()
                    .fullName("Seller1 " + uid).email("s1_def08_" + uid + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@")).phone("0912345678")
                    .role(Role.SELLER).status(UserStatus.ACTIVE).build());
            seller2 = userRepository.save(User.builder()
                    .fullName("Seller2 " + uid).email("s2_def08_" + uid + "@vietspecialty.vn")
                    .passwordHash(passwordEncoder.encode("Password123@")).phone("0912345678")
                    .role(Role.SELLER).status(UserStatus.ACTIVE).build());
            shop1 = shopRepository.save(Shop.builder()
                    .owner(seller1).name("Shop1 DEF08 " + uid).slug("shop1-def08-" + uid)
                    .province(province).addressDetail("12 Lê Lợi").phone("0912345678").status(ShopStatus.APPROVED).build());
            shop2 = shopRepository.save(Shop.builder()
                    .owner(seller2).name("Shop2 DEF08 " + uid).slug("shop2-def08-" + uid)
                    .province(province).addressDetail("34 Lê Lợi").phone("0912345678").status(ShopStatus.APPROVED).build());

            buyer = createBuyerWithAddress("def08");
            Address address = addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(buyer.getId()).get(0);
            prod1 = createShopProduct(shop1, "d08_1", BigDecimal.valueOf(200_000), 20);
            prod2 = createShopProduct(shop2, "d08_2", BigDecimal.valueOf(200_000), 20);

            String uCode = uid.toUpperCase();
            Voucher activeSys = voucherRepository.save(Voucher.builder()
                    .code("SYS_OK_" + uCode).name("Sys Valid").scope(VoucherScope.PLATFORM)
                    .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(15_000))
                    .minOrderValue(BigDecimal.ZERO).usageLimit(5).perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true).createdBy(admin).build());
            createdVouchers.add(activeSys);

            Voucher expiredSys = voucherRepository.save(Voucher.builder()
                    .code("SYS_EXP_" + uCode).name("Sys Expired").scope(VoucherScope.PLATFORM)
                    .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(15_000))
                    .minOrderValue(BigDecimal.ZERO).usageLimit(5).perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(10)).endAt(LocalDateTime.now().minusDays(1))
                    .isActive(true).createdBy(admin).build());
            createdVouchers.add(expiredSys);

            Voucher futureSys = voucherRepository.save(Voucher.builder()
                    .code("SYS_FUT_" + uCode).name("Sys Future").scope(VoucherScope.PLATFORM)
                    .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(15_000))
                    .minOrderValue(BigDecimal.ZERO).usageLimit(5).perUserLimit(1)
                    .startAt(LocalDateTime.now().plusDays(2)).endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true).createdBy(admin).build());
            createdVouchers.add(futureSys);

            Voucher fullSys = voucherRepository.save(Voucher.builder()
                    .code("SYS_FULL_" + uCode).name("Sys Exhausted").scope(VoucherScope.PLATFORM)
                    .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(15_000))
                    .minOrderValue(BigDecimal.ZERO).usageLimit(1).usedCount(1).perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true).createdBy(admin).build());
            createdVouchers.add(fullSys);

            Voucher activeShop1 = voucherRepository.save(Voucher.builder()
                    .code("SH1_OK_" + uCode).name("Shop1 Valid").scope(VoucherScope.SHOP).shop(shop1)
                    .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(10_000))
                    .minOrderValue(BigDecimal.ZERO).usageLimit(5).perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(1)).endAt(LocalDateTime.now().plusDays(5))
                    .isActive(true).createdBy(admin).build());
            createdVouchers.add(activeShop1);

            Voucher expiredShop1 = voucherRepository.save(Voucher.builder()
                    .code("SH1_EXP_" + uCode).name("Shop1 Expired").scope(VoucherScope.SHOP).shop(shop1)
                    .discountType(DiscountType.FIXED).discountValue(BigDecimal.valueOf(10_000))
                    .minOrderValue(BigDecimal.ZERO).usageLimit(5).perUserLimit(1)
                    .startAt(LocalDateTime.now().minusDays(10)).endAt(LocalDateTime.now().minusMinutes(5))
                    .isActive(true).createdBy(admin).build());
            createdVouchers.add(expiredShop1);

            // DEF-13: Checkout suggestion lists only return active, in-window, non-exhausted vouchers
            List<String> sysCodes = voucherService.getAvailableSystemVouchers().stream().map(Voucher::getCode).toList();
            assertTrue(sysCodes.contains(activeSys.getCode()));
            assertFalse(sysCodes.contains(expiredSys.getCode()));
            assertFalse(sysCodes.contains(futureSys.getCode()));
            assertFalse(sysCodes.contains(fullSys.getCode()));

            List<String> sh1Codes = voucherService.getAvailableShopVouchers(shop1.getId()).stream().map(Voucher::getCode).toList();
            assertEquals(List.of(activeShop1.getCode()), sh1Codes);

            // DEF-08: Multi-shop order applies activeSys + activeShop1
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod1.getId(), 1));
            cartService.addToCart(buyer.getId(), new CartItemRequest(prod2.getId(), 1));
            CheckoutPreviewResponse preview1 = orderService.previewCheckout(buyer.getId(), "STANDARD",
                    activeSys.getCode(), Map.of(shop1.getId(), activeShop1.getCode()));
            OrderGroup group1 = orderService.placeOrder(buyer.getId(),
                    new CheckoutFormRequest(address.getId(), "COD", "STANDARD",
                            activeSys.getCode(), Map.of(shop1.getId(), activeShop1.getCode()),
                            preview1.checkoutToken(), null),
                    "127.0.0.1");
            createdGroups.add(group1);

            List<VoucherUsage> usages = voucherUsageRepository.findByOrderGroupId(group1.getId());
            assertEquals(2, usages.size());
            assertTrue(usages.stream().allMatch(vu -> vu.getStatus() == VoucherUsageStatus.APPLIED));

            // Second use by same buyer exceeds perUserLimit=1
            final Long buyerId = buyer.getId();
            final Long shop1Id = shop1.getId();
            final Long shop2Id = shop2.getId();
            final String sysCode = activeSys.getCode();
            final String sh1Code = activeShop1.getCode();
            BusinessException sysLimitEx = assertThrows(BusinessException.class, () ->
                    voucherService.validateAndGetVoucher(sysCode, null, BigDecimal.valueOf(200_000), buyerId));
            assertEquals(ErrorCode.VOUCHER_USER_LIMIT_EXCEEDED, sysLimitEx.getErrorCode());

            BusinessException shLimitEx = assertThrows(BusinessException.class, () ->
                    voucherService.validateAndGetVoucher(sh1Code, shop1Id, BigDecimal.valueOf(200_000), buyerId));
            assertEquals(ErrorCode.VOUCHER_USER_LIMIT_EXCEEDED, shLimitEx.getErrorCode());

            // Cancel Shop 1 order only -> activeShop1 released, activeSys MUST remain APPLIED because Shop 2 is still active
            List<Order> orders = orderRepository.findByOrderGroupId(group1.getId());
            Order orderShop1 = orders.stream().filter(o -> o.getShop().getId().equals(shop1Id)).findFirst().orElseThrow();
            Order orderShop2 = orders.stream().filter(o -> o.getShop().getId().equals(shop2Id)).findFirst().orElseThrow();

            orderService.cancelOrder(orderShop1.getId(), buyer.getId(), "Hủy shop 1");
            assertEquals(0, voucherRepository.findById(activeShop1.getId()).orElseThrow().getUsedCount());
            assertEquals(1, voucherRepository.findById(activeSys.getId()).orElseThrow().getUsedCount());
            assertEquals(VoucherUsageStatus.RELEASED,
                    voucherUsageRepository.findByOrderId(orderShop1.getId()).get(0).getStatus());
            assertEquals(VoucherUsageStatus.APPLIED,
                    voucherUsageRepository.findByOrderGroupId(group1.getId()).stream()
                            .filter(vu -> vu.getOrder() == null).findFirst().orElseThrow().getStatus());

            // Cancel Shop 2 order -> now ALL orders cancelled -> activeSys is released and reusable
            orderService.cancelOrder(orderShop2.getId(), buyer.getId(), "Hủy shop 2");
            assertEquals(0, voucherRepository.findById(activeSys.getId()).orElseThrow().getUsedCount());
            assertEquals(VoucherUsageStatus.RELEASED,
                    voucherUsageRepository.findByOrderGroupId(group1.getId()).stream()
                            .filter(vu -> vu.getOrder() == null).findFirst().orElseThrow().getStatus());
            assertNotNull(voucherService.validateAndGetVoucher(activeSys.getCode(), null, BigDecimal.valueOf(200_000), buyerId));
        } finally {
            for (User u : Arrays.asList(buyer, seller1, seller2)) {
                if (u != null && u.getId() != null) {
                    notificationRepository.deleteAll(notificationRepository.findByUserIdOrderByCreatedAtDesc(u.getId(), Pageable.unpaged()).getContent());
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
}
