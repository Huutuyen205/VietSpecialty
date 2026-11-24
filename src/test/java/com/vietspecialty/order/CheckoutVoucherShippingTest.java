package com.vietspecialty.order;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.CartItemRequest;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
}
