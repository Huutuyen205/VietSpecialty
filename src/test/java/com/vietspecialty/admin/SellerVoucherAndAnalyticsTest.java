package com.vietspecialty.admin;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.ShopRegisterRequest;
import com.vietspecialty.dto.request.ShopUpdateRequest;
import com.vietspecialty.dto.request.VoucherCreateRequest;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.dto.response.VoucherResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

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
class SellerVoucherAndAnalyticsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private ProductCommentService productCommentService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

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
    private OrderRepository orderRepository;

    @Autowired
    private OrderGroupRepository orderGroupRepository;

    private User seller;
    private User admin;
    private Shop shop;

    @BeforeEach
    void setUp() {
        Province province = provinceRepository.findAll().get(0);

        seller = userRepository.save(User.builder()
                .fullName("Chủ shop Voucher Test")
                .email("seller_vchr@vietspecialty.vn")
                .passwordHash("hashed")
                .phone("0988111222")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build());

        admin = userRepository.save(User.builder()
                .fullName("Quản trị viên Hệ thống")
                .email("admin_vchr@vietspecialty.vn")
                .passwordHash("hashed")
                .phone("0999888777")
                .role(Role.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        shop = shopRepository.save(Shop.builder()
                .name("Đặc Sản Vùng Cao HTX")
                .slug("dac-san-vung-cao-htx")
                .owner(seller)
                .status(ShopStatus.APPROVED)
                .province(province)
                .addressDetail("Bản Lác, Mai Châu, Hòa Bình")
                .phone("0988111222")
                .build());
    }

    @Test
    @DisplayName("Voucher: Chủ shop tạo voucher thành công và lưu vào CSDL")
    void testCreateShopVoucherSuccess() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "SHOPTEST10",
                "Giảm 10% đơn từ 200k",
                DiscountType.PERCENT,
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(200000),
                100,
                1,
                LocalDateTime.now(),
                LocalDateTime.now().plusMonths(1),
                VoucherScope.SHOP
        );

        VoucherResponse response = voucherService.createShopVoucher(seller.getId(), request);

        assertNotNull(response);
        assertEquals("SHOPTEST10", response.code());
        assertEquals(VoucherScope.SHOP, response.scope());
        assertEquals(shop.getId(), response.shopId());
        assertTrue(response.isActive());

        // Kiểm tra lấy danh sách voucher của shop
        Page<VoucherResponse> vouchers = voucherService.getShopVouchers(shop.getId(), PageRequest.of(0, 10));
        assertFalse(vouchers.isEmpty());
        assertTrue(vouchers.stream().anyMatch(v -> v.code().equals("SHOPTEST10")));
    }

    @Test
    @DisplayName("Voucher: Tạo voucher trùng mã ném ngoại lệ BusinessException")
    void testCreateDuplicateVoucherCodeFails() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "DUPLICATE20",
                "Mã giảm giá gốc",
                DiscountType.FIXED,
                BigDecimal.valueOf(20000),
                null,
                BigDecimal.valueOf(100000),
                50,
                1,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(10),
                VoucherScope.SHOP
        );

        voucherService.createShopVoucher(seller.getId(), request);

        // Tạo lại với cùng mã
        assertThrows(BusinessException.class, () ->
                voucherService.createShopVoucher(seller.getId(), request));
    }

    @Test
    @DisplayName("Voucher: Toggle trạng thái hoạt động của voucher thành công")
    void testToggleVoucherStatus() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "TOGGLE50",
                "Mã thử nghiệm bật tắt",
                DiscountType.FIXED,
                BigDecimal.valueOf(50000),
                null,
                BigDecimal.ZERO,
                10,
                1,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(5),
                VoucherScope.SHOP
        );

        VoucherResponse created = voucherService.createShopVoucher(seller.getId(), request);
        assertTrue(created.isActive());

        // Toggle lần 1 -> Vô hiệu hóa
        voucherService.toggleVoucherStatus(created.id(), seller.getId(), false);
        Page<VoucherResponse> page1 = voucherService.getShopVouchers(shop.getId(), PageRequest.of(0, 10));
        VoucherResponse v1 = page1.stream().filter(v -> v.id().equals(created.id())).findFirst().orElseThrow();
        assertFalse(v1.isActive());

        // Toggle lần 2 -> Kích hoạt lại
        voucherService.toggleVoucherStatus(created.id(), seller.getId(), false);
        Page<VoucherResponse> page2 = voucherService.getShopVouchers(shop.getId(), PageRequest.of(0, 10));
        VoucherResponse v2 = page2.stream().filter(v -> v.id().equals(created.id())).findFirst().orElseThrow();
        assertTrue(v2.isActive());
    }

    @Test
    @DisplayName("Admin: Tạo voucher toàn sàn VietSpecialty")
    void testAdminCreatePlatformVoucher() {
        VoucherCreateRequest request = new VoucherCreateRequest(
                "VIETSPECIALTY50",
                "Trợ giá toàn sàn 50k",
                DiscountType.FIXED,
                BigDecimal.valueOf(50000),
                null,
                BigDecimal.valueOf(300000),
                500,
                2,
                LocalDateTime.now(),
                LocalDateTime.now().plusMonths(2),
                VoucherScope.PLATFORM
        );

        VoucherResponse response = voucherService.createPlatformVoucher(admin.getId(), request);

        assertNotNull(response);
        assertEquals("VIETSPECIALTY50", response.code());
        assertEquals(VoucherScope.PLATFORM, response.scope());
        assertNull(response.shopId());
    }

    @Test
    @DisplayName("Analytics: Thống kê số liệu Dashboard cho Người bán và Admin không ném lỗi")
    void testAnalyticsSummary() {
        DashboardSummaryResponse sellerSummary = analyticsService.getSellerDashboardSummary(shop.getId());
        assertNotNull(sellerSummary);
        assertNotNull(sellerSummary.totalRevenue());
        assertTrue(sellerSummary.totalOrders() >= 0);

        DashboardSummaryResponse adminSummary = analyticsService.getAdminDashboardSummary();
        assertNotNull(adminSummary);
        assertNotNull(adminSummary.totalRevenue());
        assertTrue(adminSummary.totalProducts() >= 0);
    }

    @Test
    @DisplayName("Admin: Tạm khóa gian hàng và mở lại hoạt động thành công")
    void testAdminSuspendAndReactivateShop() {
        // Tạm khóa
        ShopResponse suspended = shopService.suspendShop(shop.getId(), admin.getId());
        assertEquals(ShopStatus.SUSPENDED, suspended.status());

        // Mở lại (approve lại)
        ShopResponse reactivated = shopService.approveShop(shop.getId(), admin.getId());
        assertEquals(ShopStatus.APPROVED, reactivated.status());
    }

    @Test
    @DisplayName("Seller Center Web & CSV: Kiểm tra /seller/dashboard, /seller/questions, /seller/revenue, /seller/revenue/export (UTF-8 BOM), /seller/settings")
    void testSellerCenterPagesAndCsvExport() throws Exception {
        CustomUserDetails sellerPrincipal = new CustomUserDetails(seller);
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findAll().get(0);

        Product product = productRepository.save(Product.builder()
                .shop(shop).category(category).province(province)
                .name("Trà Shan Tuyết Cổ Thụ").slug("tra-shan-tuyet-co-thu-test")
                .price(BigDecimal.valueOf(320000)).stock(3).soldCount(10)
                .avgRating(BigDecimal.valueOf(5.0)).reviewCount(2).unit("Hộp 250g")
                .originDetail("Suối Giàng, Yên Bái").description("Trà cổ thụ")
                .status(ProductStatus.ACTIVE).build());

        // Khách hỏi Q&A trên sản phẩm
        User buyer = userRepository.save(User.builder()
                .fullName("Khách Hỏi Q&A").email("qa_buyer@vietspecialty.vn")
                .passwordHash("hashed").phone("0911222333").role(Role.USER).status(UserStatus.ACTIVE).build());
        ProductComment question = productCommentService.askQuestion(product.getId(), buyer.getId(), "Trà này pha nước bao nhiêu độ là ngon nhất ạ?");
        assertNotNull(question.getId());

        // 1. /seller/dashboard
        mockMvc.perform(get("/seller/dashboard").with(user(sellerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đặc Sản Vùng Cao HTX")));

        // 2. /seller/questions & reply
        mockMvc.perform(get("/seller/questions").with(user(sellerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Trà này pha nước bao nhiêu độ là ngon nhất ạ?")));

        mockMvc.perform(post("/seller/questions/" + question.getId() + "/reply")
                        .with(user(sellerPrincipal))
                        .with(csrf())
                        .param("reply", "Dạ quý khách pha nước sôi khoảng 85-90 độ C trong 30 giây là chuẩn vị nhất ạ!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/questions"));

        // 3. /seller/revenue & /seller/revenue/export (kiểm tra UTF-8 BOM)
        mockMvc.perform(get("/seller/revenue").with(user(sellerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thống kê Doanh thu")));

        MvcResult csvRes = mockMvc.perform(get("/seller/revenue/export").with(user(sellerPrincipal)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".csv")))
                .andReturn();
        byte[] csvBytes = csvRes.getResponse().getContentAsByteArray();
        assertTrue(csvBytes.length >= 3);
        // Kiểm tra UTF-8 BOM (0xEF, 0xBB, 0xBF)
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);
        String csvText = new String(csvBytes, StandardCharsets.UTF_8);
        assertTrue(csvText.contains("Mã đơn hàng"));

        // 4. /seller/settings
        mockMvc.perform(get("/seller/settings").with(user(sellerPrincipal)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/seller/settings")
                        .with(user(sellerPrincipal))
                        .with(csrf())
                        .param("name", "Đặc Sản Vùng Cao HTX Mới")
                        .param("hotline", "0988999000")
                        .param("pickupAddress", "Bản Lác 2, Mai Châu, Hòa Bình")
                        .param("description", "HTX nông sản sạch đạt chuẩn OCOP"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/settings"));

        Shop updatedShop = shopRepository.findById(shop.getId()).orElseThrow();
        assertEquals("Đặc Sản Vùng Cao HTX Mới", updatedShop.getName());
        assertEquals("0988999000", updatedShop.getPhone());

        // 5. /seller/vouchers/new alias
        mockMvc.perform(get("/seller/vouchers/new").with(user(sellerPrincipal)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Admin Portal Web: Kiểm tra /admin/dashboard, /admin/shops (reject & reapply), /admin/users (lock & self-lock block), /admin/regions, /admin/categories, /admin/moderation, /admin/refunds")
    void testAdminPortalPagesAndFlows() throws Exception {
        CustomUserDetails adminPrincipal = new CustomUserDetails(admin);
        Province province = provinceRepository.findAll().get(0);

        // 1. /admin/dashboard & /api/admin/analytics/regions
        mockMvc.perform(get("/admin/dashboard").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Thống Kê 4 Vùng Miền")));

        mockMvc.perform(get("/api/admin/analytics/regions").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // 2. /admin/shops: Reject shop -> User re-apply -> Approve shop
        User candidate = userRepository.save(User.builder()
                .fullName("Ứng Viên Mở Shop").email("candidate_shop@vietspecialty.vn")
                .passwordHash("hashed").phone("0933444555").role(Role.USER).status(UserStatus.ACTIVE).build());

        ShopResponse pendingShop = shopService.registerShop(candidate.getId(), new ShopRegisterRequest(
                "HTX Cam Cao Phong Hòa Bình", "Cam lòng vàng ngọt mát", "Thị trấn Cao Phong", "0933444555", province.getId()
        ), null);
        assertEquals(ShopStatus.PENDING, pendingShop.status());

        mockMvc.perform(post("/admin/shops/" + pendingShop.id() + "/reject")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("reason", "Thiếu ảnh giấy chứng nhận VSATTP"))
                .andExpect(status().is3xxRedirection());

        assertEquals(ShopStatus.REJECTED, shopRepository.findById(pendingShop.id()).orElseThrow().getStatus());

        // Candidate nộp lại hồ sơ
        ShopResponse reapplied = shopService.registerShop(candidate.getId(), new ShopRegisterRequest(
                "HTX Cam Cao Phong Chính Gốc", "Đã bổ sung giấy VSATTP", "Khu 2 Thị trấn Cao Phong", "0933444555", province.getId()
        ), null);
        assertEquals(ShopStatus.PENDING, reapplied.status());

        // Admin duyệt -> Candidate lên ROLE_SELLER
        mockMvc.perform(post("/admin/shops/" + reapplied.id() + "/approve")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(Role.SELLER, userRepository.findById(candidate.getId()).orElseThrow().getRole());

        // 3. /admin/users: Lock user, Unlock user, Chặn tự khóa chính mình
        mockMvc.perform(get("/admin/users").with(user(adminPrincipal)).param("keyword", "candidate_shop"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("candidate_shop@vietspecialty.vn")));

        mockMvc.perform(post("/admin/users/" + candidate.getId() + "/lock")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(UserStatus.LOCKED, userRepository.findById(candidate.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/admin/users/" + candidate.getId() + "/unlock")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(UserStatus.ACTIVE, userRepository.findById(candidate.getId()).orElseThrow().getStatus());

        // Chặn tự khóa chính mình
        assertThrows(BusinessException.class, () -> userService.lockUser(admin.getId(), admin.getId()));

        // 4. /admin/regions & /admin/categories
        mockMvc.perform(get("/admin/regions").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Quản Lý 4 Vùng Miền")));

        mockMvc.perform(get("/admin/categories").with(user(adminPrincipal)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/categories")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("name", "Quà Biếu Đặc Sản Cao Cấp")
                        .param("description", "Hộp quà biếu OCOP 5 sao")
                        .param("icon", "gift"))
                .andExpect(status().is3xxRedirection());

        // Gửi trùng tên danh mục không bị sập 500
        mockMvc.perform(post("/admin/categories/create")
                        .with(user(adminPrincipal))
                        .with(csrf())
                        .param("name", "Quà Biếu Đặc Sản Cao Cấp"))
                .andExpect(status().is3xxRedirection());

        // 5. /admin/moderation
        mockMvc.perform(get("/admin/moderation").with(user(adminPrincipal)))
                .andExpect(status().isOk());

        // 6. /admin/refunds: REFUND_PENDING -> REFUNDED
        OrderGroup group = orderGroupRepository.save(OrderGroup.builder()
                .code("VSG-REFUND-TEST")
                .user(candidate)
                .paymentMethod(PaymentMethod.VNPAY)
                .itemsSubtotal(BigDecimal.valueOf(200000))
                .shippingTotal(BigDecimal.valueOf(30000))
                .shopDiscountTotal(BigDecimal.ZERO)
                .platformDiscountTotal(BigDecimal.ZERO)
                .grandTotal(BigDecimal.valueOf(230000))
                .paymentStatus(OrderGroupPaymentStatus.PAID)
                .build());

        Order refundOrder = orderRepository.save(Order.builder()
                .code("VS-REFUND-01")
                .orderGroup(group)
                .user(candidate)
                .shop(shop)
                .receiverName("Ứng Viên")
                .receiverPhone("0933444555")
                .shippingAddress("Hòa Bình")
                .shippingMethod(ShippingMethod.STANDARD)
                .itemsSubtotal(BigDecimal.valueOf(200000))
                .shippingFee(BigDecimal.valueOf(30000))
                .totalAmount(BigDecimal.valueOf(230000))
                .paymentMethod(PaymentMethod.VNPAY)
                .paymentStatus(OrderPaymentStatus.REFUND_PENDING)
                .status(OrderStatus.CANCELLED)
                .build());

        mockMvc.perform(get("/admin/refunds").with(user(adminPrincipal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("VS-REFUND-01")));

        mockMvc.perform(post("/admin/refunds/" + refundOrder.getId() + "/confirm")
                        .with(user(adminPrincipal))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertEquals(OrderPaymentStatus.REFUNDED, orderRepository.findById(refundOrder.getId()).orElseThrow().getPaymentStatus());
    }
}

