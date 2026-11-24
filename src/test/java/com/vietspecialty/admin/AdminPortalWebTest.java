package com.vietspecialty.admin;

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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
class AdminPortalWebTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private RegionRepository regionRepository;

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
    private VoucherRepository voucherRepository;

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

    private Shop createApprovedShop(User owner, Province province, String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return shopRepository.save(Shop.builder()
                .name("Shop " + prefix + " " + uid)
                .slug("shop-" + prefix + "-" + uid)
                .owner(owner)
                .province(province)
                .addressDetail("12 Đường Admin Test")
                .phone("0912345678")
                .status(ShopStatus.APPROVED)
                .build());
    }

    private Review createReview(User buyer, Shop shop, Product product, int rating, String content) {
        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OrderGroup group = orderGroupRepository.save(OrderGroup.builder()
                .code("VSG-ADM-" + uid)
                .user(buyer)
                .paymentMethod(PaymentMethod.COD)
                .itemsSubtotal(product.getPrice())
                .shippingTotal(BigDecimal.ZERO)
                .grandTotal(product.getPrice())
                .paymentStatus(OrderGroupPaymentStatus.PAID)
                .build());

        Order order = Order.builder()
                .code("VS-ADM-" + uid)
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

        return reviewRepository.save(Review.builder()
                .orderItem(savedOrder.getItems().get(0))
                .product(product)
                .user(buyer)
                .rating(rating)
                .content(content)
                .status(ReviewStatus.VISIBLE)
                .build());
    }

    @Test
    @Transactional
    @DisplayName("M2-5: GET /api/admin/analytics/revenue trả JSON cho ADMIN (days=7, days=14) và 403 cho SELLER/USER")
    void testAdminRevenueAnalyticsApiAndRbac() throws Exception {
        User admin = createUser("adm_ana", Role.ADMIN);
        User seller = createUser("adm_ana_s", Role.SELLER);
        User buyer = createUser("adm_ana_b", Role.USER);
        Province province = provinceRepository.findAll().get(0);
        Shop shop = createApprovedShop(seller, province, "admana");

        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        OrderGroup group = orderGroupRepository.save(OrderGroup.builder()
                .code("VSG-AA-" + uid)
                .user(buyer)
                .paymentMethod(PaymentMethod.COD)
                .itemsSubtotal(new BigDecimal("350000"))
                .shippingTotal(new BigDecimal("20000"))
                .grandTotal(new BigDecimal("370000"))
                .paymentStatus(OrderGroupPaymentStatus.PAID)
                .build());

        orderRepository.save(Order.builder()
                .code("VS-AA-" + uid)
                .orderGroup(group)
                .user(buyer)
                .shop(shop)
                .receiverName("Khách Admin Analytics")
                .receiverPhone("0912345678")
                .shippingAddress("TP.HCM")
                .shippingMethod(ShippingMethod.STANDARD)
                .itemsSubtotal(new BigDecimal("350000"))
                .shippingFee(new BigDecimal("20000"))
                .totalAmount(new BigDecimal("370000"))
                .paymentMethod(PaymentMethod.COD)
                .paymentStatus(OrderPaymentStatus.PAID)
                .status(OrderStatus.DELIVERED)
                .build());
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        CustomUserDetails sellerDetails = new CustomUserDetails(seller);
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);
        String todayLabel = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM"));

        // ADMIN default days=7
        mockMvc.perform(get("/api/admin/analytics/revenue").with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(7)))
                .andExpect(jsonPath("$.data[6].label").value(todayLabel))
                .andExpect(jsonPath("$.data[6].revenue").value(greaterThanOrEqualTo(370000)))
                .andExpect(jsonPath("$.data[6].orderCount").value(greaterThanOrEqualTo(1)));

        // ADMIN days=14
        mockMvc.perform(get("/api/admin/analytics/revenue")
                        .param("days", "14")
                        .with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(14)));

        // SELLER -> 403 JSON
        mockMvc.perform(get("/api/admin/analytics/revenue").with(user(sellerDetails)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));

        // USER -> 403 JSON
        mockMvc.perform(get("/api/admin/analytics/revenue").with(user(buyerDetails)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Transactional
    @DisplayName("M2-5: AdminCategoryController create, edit/update, validation, toggle/status và xóa danh mục trống")
    void testAdminCategoryCreateEditToggleAndUnusedDelete() throws Exception {
        User admin = createUser("adm_cat", Role.ADMIN);
        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String catName = "Đặc Sản M2 " + uid;

        // Create
        mockMvc.perform(post("/admin/categories")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", catName)
                        .param("description", "Mô tả ban đầu")
                        .param("icon", "leaf")
                        .param("sortOrder", "99"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Category created = categoryRepository.findAll().stream()
                .filter(c -> catName.equals(c.getName()))
                .findFirst().orElseThrow();
        assertTrue(created.getIsActive());

        // Edit via /{id}/edit
        String updatedName = "Đặc Sản Sửa " + uid;
        mockMvc.perform(post("/admin/categories/" + created.getId() + "/edit")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", updatedName)
                        .param("description", "Mô tả đã sửa")
                        .param("icon", "award")
                        .param("sortOrder", "88"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();
        Category edited = categoryRepository.findById(created.getId()).orElseThrow();
        assertEquals(updatedName, edited.getName());
        assertEquals("award", edited.getIcon());

        // Blank name on /{id}/update -> flash errorMessage
        mockMvc.perform(post("/admin/categories/" + created.getId() + "/update")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("errorMessage"));

        // Toggle via /{id}/toggle (true -> false) and /{id}/status (false -> true)
        mockMvc.perform(post("/admin/categories/" + created.getId() + "/toggle")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("successMessage"));
        assertFalse(categoryRepository.findById(created.getId()).orElseThrow().getIsActive());

        mockMvc.perform(post("/admin/categories/" + created.getId() + "/status")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("successMessage"));
        assertTrue(categoryRepository.findById(created.getId()).orElseThrow().getIsActive());

        // Hard delete unused category
        mockMvc.perform(post("/admin/categories/" + created.getId() + "/delete")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();
        assertTrue(categoryRepository.findById(created.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("M2-5 (Non-Transactional): Xóa danh mục đang chứa sản phẩm rơi vào FK fallback chuyển isActive=false")
    void testAdminCategoryDeleteFkFallbackToHidden() throws Exception {
        User admin = null;
        User seller = null;
        Shop shop = null;
        Category category = null;
        Product product = null;

        try {
            admin = createUser("adm_cat_fk", Role.ADMIN);
            seller = createUser("sel_cat_fk", Role.SELLER);
            Province province = provinceRepository.findAll().get(0);
            shop = createApprovedShop(seller, province, "catfk");

            String uid = UUID.randomUUID().toString().substring(0, 8);
            category = categoryRepository.save(Category.builder()
                    .name("Danh Mục FK " + uid)
                    .slug("dm-fk-" + uid)
                    .icon("tag")
                    .sortOrder(100)
                    .isActive(true)
                    .build());

            product = productRepository.save(Product.builder()
                    .shop(shop)
                    .category(category)
                    .province(province)
                    .name("Sản Phẩm FK Category " + uid)
                    .slug("sp-fk-cat-" + uid)
                    .description("Mô tả")
                    .price(new BigDecimal("100000"))
                    .stock(5)
                    .unit("Gói")
                    .originDetail("Hà Nội")
                    .status(ProductStatus.ACTIVE)
                    .build());

            CustomUserDetails adminDetails = new CustomUserDetails(admin);
            mockMvc.perform(post("/admin/categories/" + category.getId() + "/delete")
                            .with(user(adminDetails))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/categories"))
                    .andExpect(flash().attribute("errorMessage",
                            containsString("đang chứa sản phẩm nên đã được chuyển sang trạng thái Tạm ẩn")));

            Category reloaded = categoryRepository.findById(category.getId()).orElseThrow();
            assertFalse(reloaded.getIsActive(), "Danh mục đang có sản phẩm phải chuyển sang isActive=false");
        } finally {
            if (product != null && product.getId() != null) {
                productRepository.deleteById(product.getId());
            }
            if (shop != null && shop.getId() != null) {
                shopRepository.deleteById(shop.getId());
            }
            if (seller != null && seller.getId() != null) {
                userRepository.deleteById(seller.getId());
            }
            if (admin != null && admin.getId() != null) {
                userRepository.deleteById(admin.getId());
            }
            if (category != null && category.getId() != null) {
                categoryRepository.deleteById(category.getId());
            }
        }
    }

    @Test
    @Transactional
    @DisplayName("M2-6: AdminRegionController cập nhật vùng miền (giữ nguyên colorHex) và CRUD tỉnh/thành phố trống")
    void testAdminRegionAndProvinceCrudAndUnusedDelete() throws Exception {
        User admin = createUser("adm_reg", Role.ADMIN);
        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        Region region = regionRepository.findAllByOrderBySortOrderAsc().get(0);
        Region region2 = regionRepository.findAllByOrderBySortOrderAsc().get(1);
        String originalColorHex = region.getColorHex();
        String originalName = region.getName();

        // Update region keeping exact colorHex
        mockMvc.perform(post("/admin/regions/" + region.getId() + "/update")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", originalName)
                        .param("description", "Mô tả vùng miền cập nhật trong test")
                        .param("colorHex", originalColorHex))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/regions"))
                .andExpect(flash().attributeExists("successMessage"));
        assertEquals(originalColorHex, regionRepository.findById(region.getId()).orElseThrow().getColorHex());

        // Blank region name -> flash errorMessage
        mockMvc.perform(post("/admin/regions/" + region.getId() + "/edit")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", "  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/regions"))
                .andExpect(flash().attributeExists("errorMessage"));

        // Create province
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String provName = "Tỉnh Thử Nghiệm " + uid;
        mockMvc.perform(post("/admin/regions/provinces/create")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", provName)
                        .param("regionId", String.valueOf(region.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/regions"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Province createdProv = provinceRepository.findAll().stream()
                .filter(p -> provName.equals(p.getName()))
                .findFirst().orElseThrow();

        // Duplicate province -> flash errorMessage
        mockMvc.perform(post("/admin/regions/provinces")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", provName)
                        .param("regionId", String.valueOf(region.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/regions"))
                .andExpect(flash().attributeExists("errorMessage"));

        // Update province name & reassign region
        String updatedProvName = "Tỉnh Đã Sửa " + uid;
        mockMvc.perform(post("/admin/regions/provinces/" + createdProv.getId() + "/update")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", updatedProvName)
                        .param("regionId", String.valueOf(region2.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/regions"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();
        Province updatedProv = provinceRepository.findById(createdProv.getId()).orElseThrow();
        assertEquals(updatedProvName, updatedProv.getName());
        assertEquals(region2.getId(), updatedProv.getRegion().getId());

        // Delete unused province
        mockMvc.perform(post("/admin/regions/provinces/" + createdProv.getId() + "/delete")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/regions"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();
        assertTrue(provinceRepository.findById(createdProv.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("M2-6 (Non-Transactional): Xóa tỉnh/thành phố đang có gian hàng liên kết bị chặn an toàn bởi FK protection")
    void testAdminProvinceDeleteFkProtected() throws Exception {
        User admin = null;
        User seller = null;
        Shop shop = null;
        Province province = null;

        try {
            admin = createUser("adm_prov_fk", Role.ADMIN);
            seller = createUser("sel_prov_fk", Role.SELLER);
            Region region = regionRepository.findAllByOrderBySortOrderAsc().get(0);

            String uid = UUID.randomUUID().toString().substring(0, 8);
            province = provinceRepository.save(Province.builder()
                    .name("Tỉnh FK " + uid)
                    .slug("tinh-fk-" + uid)
                    .region(region)
                    .build());

            shop = createApprovedShop(seller, province, "provfk");
            CustomUserDetails adminDetails = new CustomUserDetails(admin);

            mockMvc.perform(post("/admin/regions/provinces/" + province.getId() + "/delete")
                            .with(user(adminDetails))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/regions"))
                    .andExpect(flash().attribute("errorMessage",
                            containsString("đang có gian hàng hoặc sản phẩm liên kết nên không thể xóa")));

            assertTrue(provinceRepository.findById(province.getId()).isPresent(),
                    "Tỉnh đang có gian hàng liên kết không được phép bị xóa");
        } finally {
            if (shop != null && shop.getId() != null) {
                shopRepository.deleteById(shop.getId());
            }
            if (seller != null && seller.getId() != null) {
                userRepository.deleteById(seller.getId());
            }
            if (admin != null && admin.getId() != null) {
                userRepository.deleteById(admin.getId());
            }
            if (province != null && province.getId() != null) {
                provinceRepository.deleteById(province.getId());
            }
        }
    }

    @Test
    @Transactional
    @DisplayName("M2-6: AdminVoucherController tạo voucher toàn sàn, validation, trùng mã, toggle và chặn non-admin 403")
    void testAdminVoucherCreateValidationDuplicateToggleAndRbac() throws Exception {
        User admin = createUser("adm_vchr", Role.ADMIN);
        User seller = createUser("adm_vchr_s", Role.SELLER);
        User buyer = createUser("adm_vchr_b", Role.USER);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails adminDetails = new CustomUserDetails(admin);
        CustomUserDetails sellerDetails = new CustomUserDetails(seller);
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        String startStr = LocalDateTime.now().plusHours(1).format(fmt);
        String endStr = LocalDateTime.now().plusDays(14).format(fmt);
        String code = "PLAT" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // 1. Valid create -> 302 /admin/vouchers
        mockMvc.perform(post("/admin/vouchers/create")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("code", code.toLowerCase())
                        .param("name", "Voucher Toàn Sàn M2")
                        .param("discountType", "PERCENT")
                        .param("discountValue", "10")
                        .param("maxDiscount", "100000")
                        .param("minOrderValue", "200000")
                        .param("usageLimit", "100")
                        .param("perUserLimit", "2")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/vouchers"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Voucher created = voucherRepository.findByCodeIgnoreCase(code).orElseThrow();
        assertEquals(code, created.getCode());
        assertEquals(VoucherScope.PLATFORM, created.getScope());
        assertNull(created.getShop());
        assertTrue(created.getIsActive());

        // 2. Bean validation errors on /new -> 200 admin/vouchers/form
        mockMvc.perform(post("/admin/vouchers/new")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("code", "")
                        .param("name", "Lỗi validation")
                        .param("discountType", "FIXED")
                        .param("discountValue", "0")
                        .param("minOrderValue", "0")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/vouchers/form"))
                .andExpect(model().attributeHasFieldErrors("voucherForm", "code", "discountValue"));

        // 3. Duplicate code -> 200 admin/vouchers/form + errorMessage
        mockMvc.perform(post("/admin/vouchers/create")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("code", code)
                        .param("name", "Trùng mã")
                        .param("discountType", "FIXED")
                        .param("discountValue", "30000")
                        .param("minOrderValue", "100000")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/vouchers/form"))
                .andExpect(model().attributeExists("errorMessage"));

        // 4. Toggle -> 302 /admin/vouchers, flips isActive
        mockMvc.perform(post("/admin/vouchers/" + created.getId() + "/toggle")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/vouchers"))
                .andExpect(flash().attributeExists("successMessage"));
        assertFalse(voucherRepository.findById(created.getId()).orElseThrow().getIsActive());

        // 5. Non-admin 403
        mockMvc.perform(post("/admin/vouchers/create")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("code", "NOAUTH1")
                        .param("name", "Không quyền")
                        .param("discountType", "FIXED")
                        .param("discountValue", "10000")
                        .param("minOrderValue", "0")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/vouchers/create")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("code", "NOAUTH2")
                        .param("name", "Không quyền")
                        .param("discountType", "FIXED")
                        .param("discountValue", "10000")
                        .param("minOrderValue", "0")
                        .param("startAt", startStr)
                        .param("endAt", endStr))
                .andExpect(status().isForbidden());
    }

    @Test
    @Transactional
    @DisplayName("M2-6: AdminModerationController hide/toggle/show/status cập nhật ReviewStatus và tính lại Product.avgRating & reviewCount")
    void testAdminReviewModerationAndAvgRatingRecalc() throws Exception {
        User admin = createUser("adm_mod", Role.ADMIN);
        User seller = createUser("adm_mod_s", Role.SELLER);
        User buyer1 = createUser("adm_mod_b1", Role.USER);
        User buyer2 = createUser("adm_mod_b2", Role.USER);
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0);
        Shop shop = createApprovedShop(seller, province, "admmod");

        String uid = UUID.randomUUID().toString().substring(0, 8);
        Product product = productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name("Chả Mực Hạ Long Moderation " + uid)
                .slug("cha-muc-mod-" + uid)
                .description("Đặc sản Quảng Ninh")
                .price(new BigDecimal("420000"))
                .stock(20)
                .unit("Kg")
                .originDetail("Hạ Long, Quảng Ninh")
                .avgRating(new BigDecimal("4.0"))
                .reviewCount(2)
                .status(ProductStatus.ACTIVE)
                .build());

        Review review5 = createReview(buyer1, shop, product, 5, "Chả mực giã tay rất ngon!");
        Review review3 = createReview(buyer2, shop, product, 3, "Hơi mặn một chút");
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails adminDetails = new CustomUserDetails(admin);

        // 1. Hide 3-star review -> avgRating = 5.0, reviewCount = 1
        mockMvc.perform(post("/admin/moderation/reviews/" + review3.getId() + "/hide")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/moderation?tab=reviews"))
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals(ReviewStatus.HIDDEN, reviewRepository.findById(review3.getId()).orElseThrow().getStatus());
        Product afterHide3 = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5.0").compareTo(afterHide3.getAvgRating()));
        assertEquals(1, afterHide3.getReviewCount());

        // 2. Toggle 5-star review (VISIBLE -> HIDDEN) -> avgRating = 0, reviewCount = 0
        mockMvc.perform(post("/admin/moderation/reviews/" + review5.getId() + "/toggle")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/moderation?tab=reviews"))
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals(ReviewStatus.HIDDEN, reviewRepository.findById(review5.getId()).orElseThrow().getStatus());
        Product afterHideBoth = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(afterHideBoth.getAvgRating()));
        assertEquals(0, afterHideBoth.getReviewCount());

        // 3. Show 5-star review -> avgRating = 5.0, reviewCount = 1
        mockMvc.perform(post("/admin/moderation/reviews/" + review5.getId() + "/show")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/moderation?tab=reviews"))
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals(ReviewStatus.VISIBLE, reviewRepository.findById(review5.getId()).orElseThrow().getStatus());
        Product afterShow5 = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("5.0").compareTo(afterShow5.getAvgRating()));
        assertEquals(1, afterShow5.getReviewCount());

        // 4. Status endpoint (visible=true) on 3-star review -> avgRating = 4.0, reviewCount = 2
        mockMvc.perform(post("/admin/moderation/reviews/" + review3.getId() + "/status")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("visible", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/moderation?tab=reviews"))
                .andExpect(flash().attributeExists("successMessage"));

        assertEquals(ReviewStatus.VISIBLE, reviewRepository.findById(review3.getId()).orElseThrow().getStatus());
        Product afterRestoreAll = productRepository.findById(product.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("4.0").compareTo(afterRestoreAll.getAvgRating()));
        assertEquals(2, afterRestoreAll.getReviewCount());
    }
}
