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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

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
@Transactional
class SellerVoucherAndAnalyticsWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private VoucherRepository voucherRepository;

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
                .addressDetail("101 Đường Voucher")
                .phone("0912345678")
                .status(ShopStatus.APPROVED)
                .build());
    }

    @Test
    @DisplayName("POST /seller/vouchers/create và /new tạo voucher shop hợp lệ, kiểm tra validation, trùng mã và endAt < startAt")
    void testCreateSellerVoucherValidationAndDuplicates() throws Exception {
        User seller = createUser("vchr_s1", Role.SELLER);
        Shop shop = createApprovedShop(seller, "vchrs1");
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String startStr = LocalDateTime.now().plusHours(1).format(fmt);
        String endStr = LocalDateTime.now().plusDays(7).format(fmt);
        String code = "SHOP" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 1. Valid create -> 302 /seller/vouchers
        mockMvc.perform(post("/seller/vouchers/create")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("code", code.toLowerCase())
                        .param("name", "Giảm 15% đặc sản")
                        .param("discountType", "PERCENT")
                        .param("discountValue", "15")
                        .param("maxDiscount", "50000")
                        .param("minOrderValue", "100000")
                        .param("usageLimit", "50")
                        .param("perUserLimit", "1")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/vouchers"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Voucher saved = voucherRepository.findByCodeIgnoreCase(code).orElseThrow();
        assertEquals(code, saved.getCode());
        assertEquals(VoucherScope.SHOP, saved.getScope());
        assertEquals(shop.getId(), saved.getShop().getId());
        assertTrue(saved.getIsActive());

        // 2. Duplicate code -> 200 seller/vouchers/form + errorMessage
        mockMvc.perform(post("/seller/vouchers/new")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("code", code)
                        .param("name", "Mã trùng")
                        .param("discountType", "FIXED")
                        .param("discountValue", "20000")
                        .param("minOrderValue", "50000")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/vouchers/form"))
                .andExpect(model().attributeExists("errorMessage"));

        // 3. endAt < startAt -> 200 seller/vouchers/form + errorMessage
        String otherCode = "DATE" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        mockMvc.perform(post("/seller/vouchers/create")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("code", otherCode)
                        .param("name", "Sai ngày")
                        .param("discountType", "FIXED")
                        .param("discountValue", "20000")
                        .param("minOrderValue", "50000")
                        .param("startAt", endStr)
                        .param("endAt", startStr))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/vouchers/form"))
                .andExpect(model().attributeExists("errorMessage"));

        // 4. Bean Validation errors: blank code + discountValue = 0
        mockMvc.perform(post("/seller/vouchers/create")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("code", "")
                        .param("name", "Lỗi validation")
                        .param("discountType", "FIXED")
                        .param("discountValue", "0")
                        .param("minOrderValue", "0")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/vouchers/form"))
                .andExpect(model().attributeHasFieldErrors("voucherForm", "code", "discountValue"));
    }

    @Test
    @DisplayName("POST /seller/vouchers/{id}/toggle cho chủ shop thành công và chặn cross-seller IDOR")
    void testToggleSellerVoucherAndIdor() throws Exception {
        User sellerA = createUser("vchr_a", Role.SELLER);
        Shop shopA = createApprovedShop(sellerA, "vchra");
        User sellerB = createUser("vchr_b", Role.SELLER);
        createApprovedShop(sellerB, "vchrb");

        String code = "TGL" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        Voucher voucher = voucherRepository.save(Voucher.builder()
                .code(code)
                .name("Voucher Toggle Test")
                .scope(VoucherScope.SHOP)
                .shop(shopA)
                .discountType(DiscountType.FIXED)
                .discountValue(new BigDecimal("25000"))
                .minOrderValue(new BigDecimal("100000"))
                .startAt(LocalDateTime.now())
                .endAt(LocalDateTime.now().plusDays(10))
                .isActive(true)
                .createdBy(sellerA)
                .build());
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerADetails = new CustomUserDetails(sellerA);
        CustomUserDetails sellerBDetails = new CustomUserDetails(sellerB);

        // Owner toggle true -> false
        mockMvc.perform(post("/seller/vouchers/" + voucher.getId() + "/toggle")
                        .with(user(sellerADetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/vouchers"))
                .andExpect(flash().attributeExists("successMessage"));
        assertFalse(voucherRepository.findById(voucher.getId()).orElseThrow().getIsActive());

        // Cross-seller IDOR -> 400 error/500 AUTH_002, isActive unchanged
        mockMvc.perform(post("/seller/vouchers/" + voucher.getId() + "/toggle")
                        .with(user(sellerBDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "AUTH_002"));
        assertFalse(voucherRepository.findById(voucher.getId()).orElseThrow().getIsActive());
    }

    @Test
    @DisplayName("GET /api/seller/analytics/revenue trả JSON cho SELLER, 403 cho USER/ADMIN và 401 cho guest")
    void testSellerAnalyticsRevenueApiAndRbac() throws Exception {
        User seller = createUser("ana_s", Role.SELLER);
        Shop shop = createApprovedShop(seller, "anas");
        User buyer = createUser("ana_b", Role.USER);
        User admin = createUser("ana_admin", Role.ADMIN);

        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OrderGroup group = orderGroupRepository.save(OrderGroup.builder()
                .code("VSG-ANA-" + uid)
                .user(buyer)
                .paymentMethod(PaymentMethod.COD)
                .itemsSubtotal(new BigDecimal("450000"))
                .shippingTotal(new BigDecimal("30000"))
                .grandTotal(new BigDecimal("480000"))
                .paymentStatus(OrderGroupPaymentStatus.PAID)
                .build());

        orderRepository.save(Order.builder()
                .code("VS-ANA-" + uid)
                .orderGroup(group)
                .user(buyer)
                .shop(shop)
                .receiverName("Khách Thống Kê")
                .receiverPhone("0912345678")
                .shippingAddress("Đà Nẵng")
                .shippingMethod(ShippingMethod.STANDARD)
                .itemsSubtotal(new BigDecimal("450000"))
                .shippingFee(new BigDecimal("30000"))
                .totalAmount(new BigDecimal("480000"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(OrderPaymentStatus.PAID)
                .status(OrderStatus.DELIVERED)
                .build());
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);
        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        String todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"));

        // SELLER -> 200 JSON with 7 buckets and today's revenue/orderCount
        mockMvc.perform(get("/api/seller/analytics/revenue")
                        .param("days", "7")
                        .with(user(sellerDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[6].label").value(todayLabel))
                .andExpect(jsonPath("$.data[6].revenue").value(480000))
                .andExpect(jsonPath("$.data[6].orderCount").value(1));

        // USER -> 403 JSON
        mockMvc.perform(get("/api/seller/analytics/revenue").with(user(buyerDetails)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // ADMIN -> 403 JSON (ADMIN does not imply SELLER)
        mockMvc.perform(get("/api/seller/analytics/revenue").with(user(adminDetails)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // Guest -> 401 JSON
        mockMvc.perform(get("/api/seller/analytics/revenue"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
