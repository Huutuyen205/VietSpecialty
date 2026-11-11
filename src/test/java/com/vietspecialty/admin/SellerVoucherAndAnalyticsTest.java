package com.vietspecialty.admin;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.VoucherCreateRequest;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.dto.response.VoucherResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.*;
import com.vietspecialty.service.AnalyticsService;
import com.vietspecialty.service.ShopService;
import com.vietspecialty.service.VoucherService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class SellerVoucherAndAnalyticsTest {

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

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
}

