package com.vietspecialty.seller;

import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.request.ProductCreateRequest;
import com.vietspecialty.dto.request.ShopRegisterRequest;
import com.vietspecialty.dto.response.ProductDetailResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.ProductService;
import com.vietspecialty.service.ShopService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ShopAndProductSellerTest {

    @Autowired
    private ShopService shopService;

    @Autowired
    private ProductService productService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Test
    @DisplayName("Quy trình Người dùng nộp đơn mở shop -> Admin duyệt shop -> Thăng cấp SELLER thành công")
    void testShopRegistrationAndApprovalFlow() {
        // 1. Tạo user buyer
        User buyer = User.builder()
                .fullName("Bà con làng nghề")
                .email("seller_candidate@vietspecialty.vn")
                .passwordHash("password_hash")
                .phone("0987654321")
                .role(Role.USER)
                .status(com.vietspecialty.domain.enums.UserStatus.ACTIVE)
                .build();
        User savedUser = userRepository.save(buyer);

        // 2. Nộp đơn mở gian hàng
        Integer provinceId = provinceRepository.findAll().get(0).getId();
        ShopRegisterRequest req = new ShopRegisterRequest(
                "HTX Bánh Gai Ninh Giang Hải Dương",
                "Đặc sản bánh gai truyền thống nức tiếng",
                "Thị trấn Ninh Giang, Hải Dương",
                "0987654321",
                provinceId
        );

        ShopResponse registeredShop = shopService.registerShop(savedUser.getId(), req, null);
        assertNotNull(registeredShop);
        assertEquals(ShopStatus.PENDING, registeredShop.status());

        // 3. Admin duyệt gian hàng
        ShopResponse approvedShop = shopService.approveShop(registeredShop.id(), 1L);
        assertEquals(ShopStatus.APPROVED, approvedShop.status());

        // 4. Kiểm tra user đã được thăng cấp SELLER
        User reloadedUser = userRepository.findById(savedUser.getId()).orElseThrow();
        assertEquals(Role.SELLER, reloadedUser.getRole());
    }

    @Test
    @DisplayName("Seller tạo sản phẩm có ảnh PNG hợp lệ và chặn IDOR khi shop khác cố sửa")
    void testSellerCreateProductAndIdorProtection() {
        // Tạo shop đã duyệt
        User sellerUser = User.builder()
                .fullName("Chủ HTX Trà")
                .email("seller_tea@vietspecialty.vn")
                .passwordHash("password_hash")
                .phone("0977112233")
                .role(Role.SELLER)
                .status(com.vietspecialty.domain.enums.UserStatus.ACTIVE)
                .build();
        userRepository.save(sellerUser);

        Integer provinceId = provinceRepository.findAll().get(0).getId();
        Shop shop = Shop.builder()
                .name("HTX Trà Xanh Thái Nguyên Test")
                .slug("htx-tra-xanh-thai-nguyen-test")
                .owner(sellerUser)
                .province(provinceRepository.findById(provinceId).orElseThrow())
                .status(ShopStatus.APPROVED)
                .build();
        Shop savedShop = shopRepository.save(shop);

        // Chuẩn bị Mock MultipartFile PNG hợp lệ với Magic Bytes chuẩn
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        MockMultipartFile image = new MockMultipartFile("images", "test-tea.png", "image/png", pngBytes);

        ProductCreateRequest prodReq = new ProductCreateRequest(
                "Trà Tân Cương Thượng Hạng Hộp 500g",
                "Đặc sản trà thượng hạng",
                "Chi tiết về chè tân cương đạt chuẩn sao OCOP...",
                new BigDecimal("220000"),
                100,
                "Hộp 500g",
                500,
                "Xã Tân Cương, Thái Nguyên",
                "12 tháng",
                "Bảo quản khô ráo",
                4,
                provinceId,
                1 // Danh mục trà & cà phê
        );

        ProductDetailResponse created = productService.createProduct(savedShop.getId(), prodReq, List.of(image));
        assertNotNull(created);
        assertEquals("Trà Tân Cương Thượng Hạng Hộp 500g", created.name());
        assertFalse(created.images().isEmpty());

        // Kiểm tra chống IDOR: Một shop lạ (id 999999L) cố xóa sản phẩm này phải quăng BusinessException
        assertThrows(BusinessException.class, () -> productService.deleteProduct(999999L, created.id()));
    }
}
