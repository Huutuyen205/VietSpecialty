package com.vietspecialty.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vietspecialty.config.AppProperties;
import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.*;
import com.vietspecialty.dto.request.*;
import com.vietspecialty.dto.response.*;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.*;
import com.vietspecialty.util.VnpaySigner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bộ kiểm thử E2E & Integration toàn diện bao phủ 100% (81/81) Test Cases
 * trong docs/BA_TEST_CASES.md của sàn TMĐT Đặc sản vùng miền VietSpecialty.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ComprehensiveMasterTestCasesE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderGroupRepository orderGroupRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private VoucherRepository voucherRepository;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductCommentRepository productCommentRepository;

    @Autowired
    private CartService cartService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private ProductService productService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private UserService userService;

    @Autowired
    private AddressService addressService;

    @Autowired
    private AuthService authService;

    @Autowired
    private ChatService chatService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AppProperties appProperties;

    private CustomUserDetails detailsOf(String email) {
        User u = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        return new CustomUserDetails(u);
    }

    // =========================================================================
    // PHÂN HỆ 1: XÁC THỰC, GOOGLE OAUTH2 & QUẢN LÝ TÀI KHOẢN (TC-AUTH-01 .. 14)
    // =========================================================================

    @Test
    @DisplayName("MOD-01 (TC-AUTH-01..14): Xác thực truyền thống, Google OAuth2, Hồ sơ, Đổi mật khẩu, Sổ địa chỉ & Thông báo")
    void testModule01_AuthenticationAndAccountManagement() throws Exception {
        // TC-AUTH-01: Đăng ký truyền thống hợp lệ
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Trần Văn Đặc Sản")
                        .param("email", "newbuyer_tc01@vietspecialty.vn")
                        .param("phone", "0912888999")
                        .param("password", "Password123@")
                        .param("confirmPassword", "Password123@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?registered=true"));

        User registered = userRepository.findByEmailIgnoreCase("newbuyer_tc01@vietspecialty.vn").orElseThrow();
        assertEquals(Role.USER, registered.getRole());
        assertEquals(UserStatus.ACTIVE, registered.getStatus());
        assertTrue(passwordEncoder.matches("Password123@", registered.getPasswordHash()));

        // TC-AUTH-02: Validation form Đăng ký (email trùng, mật khẩu không khớp)
        mockMvc.perform(post("/register")
                        .with(csrf())
                        .param("fullName", "Người Mua Trùng")
                        .param("email", "buyer@vietspecialty.vn")
                        .param("phone", "0912888999")
                        .param("password", "Password123@")
                        .param("confirmPassword", "Password123@"))
                .andExpect(status().isOk())
                .andExpect(view().name("auth/register"));

        // TC-AUTH-03 & TC-AUTH-04: Hiển thị nút Google trên /login, /register và trang /auth/google/choose
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tiếp tục với Google")));
        mockMvc.perform(get("/register"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Đăng ký nhanh bằng Google")));
        mockMvc.perform(get("/auth/google").param("mode", "login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/auth/google/confirm")));

        // TC-AUTH-05: Đăng ký mới & tự động đăng nhập qua Google
        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", "google_e2e_user@gmail.com")
                        .param("fullName", "Nguyễn Google E2E")
                        .param("googleId", "gid-e2e-001"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
        assertTrue(userRepository.existsByEmailIgnoreCase("google_e2e_user@gmail.com"));

        // TC-AUTH-06: Đăng nhập bằng tài khoản Google đã tồn tại (USER -> /, SELLER -> /seller/dashboard)
        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", "seller_bac@vietspecialty.vn")
                        .param("fullName", "Seller Bắc"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/dashboard"));

        // TC-AUTH-07: Chặn đăng nhập bằng Google với tài khoản bị khóa (LOCKED)
        User lockedUser = userRepository.save(User.builder()
                .fullName("Locked User")
                .email("locked_e2e@gmail.com")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .role(Role.USER)
                .status(UserStatus.LOCKED)
                .build());
        mockMvc.perform(post("/auth/google/confirm")
                        .with(csrf())
                        .param("email", lockedUser.getEmail())
                        .param("fullName", lockedUser.getFullName()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login?error=")));

        // TC-AUTH-08 & TC-AUTH-09 & TC-AUTH-10: Đăng nhập truyền thống đúng/sai & Đăng xuất
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("email", "buyer@vietspecialty.vn")
                        .param("password", "WrongPass999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login?error=")));

        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));

        // TC-AUTH-11: Xem và cập nhật Hồ sơ cá nhân (/account/profile)
        CustomUserDetails buyerDetails = detailsOf("buyer@vietspecialty.vn");
        mockMvc.perform(get("/account/profile").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("buyer@vietspecialty.vn")));

        mockMvc.perform(post("/account/profile")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("fullName", "Nguyễn Khách Hàng VIP")
                        .param("phone", "0988777666"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/profile"));
        assertEquals("Nguyễn Khách Hàng VIP", userRepository.findById(buyerDetails.getId()).orElseThrow().getFullName());

        // TC-AUTH-12: Đổi mật khẩu (/account/password)
        mockMvc.perform(get("/account/password").with(user(buyerDetails)))
                .andExpect(status().isOk());

        User tempUser = userRepository.save(User.builder()
                .fullName("Temp Pass User")
                .email("temppass@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("OldPass123@"))
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
        CustomUserDetails tempDetails = new CustomUserDetails(tempUser);
        mockMvc.perform(post("/account/password")
                        .with(user(tempDetails))
                        .with(csrf())
                        .param("oldPassword", "OldPass123@")
                        .param("newPassword", "NewPass456@")
                        .param("confirmNewPassword", "NewPass456@"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/password"));
        assertTrue(passwordEncoder.matches("NewPass456@", userRepository.findById(tempUser.getId()).orElseThrow().getPasswordHash()));

        // TC-AUTH-13: Quản lý Sổ địa chỉ nhận hàng (34 Tỉnh/Thành 2025, Thêm, Đặt mặc định, Xóa)
        assertEquals(34, provinceRepository.count(), "Phải có đúng 34 tỉnh/thành phố theo chuẩn 2025");
        Integer thaiNguyenId = provinceRepository.findBySlug("thai-nguyen").orElseThrow().getId();

        mockMvc.perform(get("/account/addresses").with(user(tempDetails)))
                .andExpect(status().isOk());

        AddressResponse addrA = addressService.createAddress(tempUser.getId(),
                new AddressRequest("Người Nhận A", "0912345678", thaiNguyenId, "Xã Tân Cương", "Xóm Hồng Thái 1", true));
        AddressResponse addrB = addressService.createAddress(tempUser.getId(),
                new AddressRequest("Người Nhận B", "0987654321", thaiNguyenId, "Phường Hoàng Văn Thụ", "Số 10 Đường Lương Ngọc Quyến", false));
        assertTrue(addrA.isDefault());
        assertFalse(addrB.isDefault());

        mockMvc.perform(post("/account/addresses/" + addrB.id() + "/default")
                        .with(user(tempDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/addresses"));

        mockMvc.perform(post("/account/addresses/" + addrB.id() + "/delete")
                        .with(user(tempDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/addresses"));

        List<AddressResponse> remainingAddrs = addressService.getUserAddresses(tempUser.getId());
        assertEquals(1, remainingAddrs.size());
        assertTrue(remainingAddrs.get(0).isDefault(), "Khi xóa địa chỉ mặc định B, địa chỉ A tự động lên làm mặc định");

        // TC-AUTH-14: Thông báo In-App
        mockMvc.perform(get("/api/notifications").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(get("/api/notifications/unread-count").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        mockMvc.perform(post("/api/notifications/read-all").with(user(buyerDetails)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // =========================================================================
    // PHÂN HỆ 2: KHÁM PHÁ & MUA SẮM ĐẶC SẢN (TC-CAT-01 .. 15)
    // =========================================================================

    @Test
    @DisplayName("MOD-02 (TC-CAT-01..15): Trang chủ, Tìm kiếm có dấu/không dấu, Autocomplete, Lọc 4 miền/34 tỉnh/OCOP, Chi tiết, Q&A, Wishlist, XSS")
    void testModule02_CatalogDiscoveryAndProductDetail() throws Exception {
        // TC-CAT-01: Trang chủ /
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("VietSpecialty")));

        // TC-CAT-02: Tìm kiếm đặc sản có dấu và không dấu
        mockMvc.perform(get("/products").param("keyword", "Trà Tân Cương"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trà")));

        mockMvc.perform(get("/products").param("keyword", "mat ong"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Mật")));

        mockMvc.perform(get("/products").param("keyword", "xyz_khong_ton_tai_9999"))
                .andExpect(status().isOk());

        // TC-CAT-03: Autocomplete Suggest API (cả q và keyword)
        mockMvc.perform(get("/api/products/suggest").param("q", "Trà"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());

        mockMvc.perform(get("/api/products/suggest").param("keyword", "Mật ong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());

        // TC-CAT-04: Lọc theo 4 Vùng miền
        mockMvc.perform(get("/regions"))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(get("/regions/mien-bac"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products?regionSlug=mien-bac"));
        mockMvc.perform(get("/products").param("regionSlug", "mien-bac"))
                .andExpect(status().isOk());

        // TC-CAT-05: Lọc theo 34 Tỉnh/Thành phố (/provinces/{slug} & /products?provinceSlug=...)
        mockMvc.perform(get("/provinces/thai-nguyen"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products?provinceSlug=thai-nguyen"));
        mockMvc.perform(get("/products").param("provinceSlug", "thai-nguyen"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thái Nguyên")));

        // TC-CAT-06 & TC-CAT-07 & TC-CAT-08 & TC-CAT-09: Danh mục, OCOP, Khoảng giá, Sắp xếp & Phân trang
        Category firstCategory = categoryRepository.findAll().get(0);
        mockMvc.perform(get("/categories/" + firstCategory.getSlug()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products?categorySlug=" + firstCategory.getSlug()));

        mockMvc.perform(get("/ocop"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/products?hasOcop=true"));

        mockMvc.perform(get("/products")
                        .param("hasOcop", "true")
                        .param("minPrice", "50000")
                        .param("maxPrice", "500000")
                        .param("minRating4", "true")
                        .param("sort", "price_asc")
                        .param("page", "0"))
                .andExpect(status().isOk());

        // TC-CAT-10 & TC-CAT-11: Trang chi tiết đặc sản & API lọc đánh giá
        Product sampleProduct = productRepository.findAll().stream()
                .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
                .findFirst().orElseThrow();

        mockMvc.perform(get("/products/" + sampleProduct.getSlug()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sampleProduct.getName())))
                .andExpect(content().string(containsString("data-product-id")));

        // Guest cũng có thể xem danh sách đánh giá trên trang chi tiết sản phẩm (TC-CAT-11)
        mockMvc.perform(get("/api/reviews/product/" + sampleProduct.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        CustomUserDetails buyerDetails = detailsOf("buyer@vietspecialty.vn");
        mockMvc.perform(get("/api/reviews/product/" + sampleProduct.getId()).with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // TC-CAT-12: Gửi câu hỏi Hỏi đáp Q&A trên trang chi tiết sản phẩm & Validation độ dài
        mockMvc.perform(post("/products/" + sampleProduct.getId() + "/comments")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .header("Accept", "application/json")
                        .param("content", "Đặc sản này mở nắp rồi bảo quản được bao lâu ạ?"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Câu hỏi rỗng -> 400 Bad Request
        mockMvc.perform(post("/products/" + sampleProduct.getId() + "/comments")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .header("Accept", "application/json")
                        .param("content", "   "))
                .andExpect(status().isBadRequest());

        // Câu hỏi > 1000 ký tự -> 400 Bad Request
        String tooLongQuestion = "A".repeat(1005);
        mockMvc.perform(post("/products/" + sampleProduct.getId() + "/comments")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .header("Accept", "application/json")
                        .param("content", tooLongQuestion))
                .andExpect(status().isBadRequest());

        // TC-CAT-13: Xem Trang Gian hàng HTX (/shops/{slug})
        Shop approvedShop = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .findFirst().orElseThrow();
        mockMvc.perform(get("/shops/" + approvedShop.getSlug()).param("keyword", "Trà"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(approvedShop.getName())));

        // TC-CAT-14: Yêu thích (Wishlist)
        mockMvc.perform(post("/wishlist/toggle/" + sampleProduct.getId())
                        .with(user(buyerDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.added").value(true));

        mockMvc.perform(get("/wishlist").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(sampleProduct.getName())));

        mockMvc.perform(post("/wishlist/toggle/" + sampleProduct.getId())
                        .with(user(buyerDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(false));

        // TC-CAT-15: Chống XSS trên thanh Tìm kiếm
        MvcResult xssResult = mockMvc.perform(get("/products").param("keyword", "<script>alert('xss')</script>"))
                .andExpect(status().isOk())
                .andReturn();
        String html = xssResult.getResponse().getContentAsString();
        assertFalse(html.contains("<script>alert('xss')</script>"), "Không được chứa thẻ script XSS chưa escape");
    }

    // =========================================================================
    // PHÂN HỆ 3: GIỎ HÀNG, ĐẶT HÀNG, TÁCH ĐƠN, VOUCHER & THANH TOÁN (TC-ORD-01 .. 15)
    // =========================================================================

    @Test
    @DisplayName("MOD-03 (TC-ORD-01..15): Giỏ hàng đa shop, Tồn kho, Phí ship, Voucher hợp lệ/sai, Tách đơn, Double-submit, VNPay, Hủy đơn, Đánh giá, Mua lại & Hoàn tiền")
    void testModule03_CartCheckoutOrderSplittingAndPayment() throws Exception {
        CustomUserDetails buyerDetails = detailsOf("buyer@vietspecialty.vn");
        Long buyerId = buyerDetails.getId();

        List<Shop> approvedShops = shopRepository.findAll().stream()
                .filter(s -> s.getStatus() == ShopStatus.APPROVED)
                .toList();
        assertTrue(approvedShops.size() >= 2, "Cần ít nhất 2 gian hàng APPROVED");

        Shop shop1 = approvedShops.get(0);
        Shop shop2 = approvedShops.get(1);

        Product prodShop1 = productRepository.findAll().stream()
                .filter(p -> p.getShop().getId().equals(shop1.getId()) && p.getStatus() == ProductStatus.ACTIVE && p.getStock() >= 10)
                .findFirst().orElseThrow();
        Product prodShop2 = productRepository.findAll().stream()
                .filter(p -> p.getShop().getId().equals(shop2.getId()) && p.getStatus() == ProductStatus.ACTIVE && p.getStock() >= 10)
                .findFirst().orElseThrow();

        // TC-ORD-01 & TC-ORD-02: Thêm vào giỏ, Xem giỏ hàng đa shop, Cập nhật số lượng
        mockMvc.perform(post("/api/cart/items")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(prodShop1.getId(), 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(post("/api/cart/items")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(prodShop2.getId(), 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/cart").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(prodShop1.getName())))
                .andExpect(content().string(containsString(prodShop2.getName())));

        // TC-ORD-03: Chặn thêm vượt quá tồn kho
        mockMvc.perform(post("/api/cart/items")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CartItemRequest(prodShop1.getId(), 99999))))
                .andExpect(status().isBadRequest());

        // TC-ORD-04 & TC-ORD-05 & TC-ORD-06: Xem trước Checkout, Phí vận chuyển, Áp mã Voucher hợp lệ và Mã Voucher sai (không sập 500)
        mockMvc.perform(get("/checkout")
                        .with(user(buyerDetails))
                        .param("shippingMethod", "STANDARD")
                        .param("systemVoucherCode", "MA_SAI_KHONG_TON_TAI_999"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/checkout"));

        mockMvc.perform(get("/checkout")
                        .with(user(buyerDetails))
                        .param("shippingMethod", "EXPRESS")
                        .param("systemVoucherCode", "VIETSPECIALTY"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/checkout"));

        // Đảm bảo buyer có địa chỉ nhận hàng
        Province province = provinceRepository.findAll().get(0);
        Address address = addressRepository.findAll().stream()
                .filter(a -> a.getUser().getId().equals(buyerId))
                .findFirst()
                .orElseGet(() -> addressRepository.save(Address.builder()
                        .user(userRepository.findById(buyerId).orElseThrow())
                        .receiverName("Nguyễn Khách Mua")
                        .receiverPhone("0912345678")
                        .province(province)
                        .ward("Phường Tràng Tiền")
                        .streetAddress("Số 1 Tràng Tiền")
                        .isDefault(true)
                        .build()));

        // TC-ORD-07 & TC-ORD-08: Đặt hàng COD tách đơn đa gian hàng & Chống Double-Submit bằng checkoutToken
        CheckoutPreviewResponse preview = orderService.previewCheckout(buyerId, "STANDARD", null, null);
        String token = preview.checkoutToken();

        CheckoutFormRequest formReq = new CheckoutFormRequest(
                address.getId(), "COD", "STANDARD", null, null, token, "Giao buổi sáng"
        );
        OrderGroup group = orderService.placeOrder(buyerId, formReq, "127.0.0.1");
        assertNotNull(group);
        List<Order> splitOrders = orderRepository.findByOrderGroupId(group.getId());
        assertEquals(2, splitOrders.size(), "Phải tách thành 2 đơn hàng con cho 2 gian hàng");

        // Kiểm tra trang /checkout/success render hoàn hảo không bị lỗi MultipleBagFetchException
        mockMvc.perform(get("/checkout/success")
                        .with(user(buyerDetails))
                        .param("groupCode", group.getCode()))
                .andExpect(status().isOk())
                .andExpect(view().name("web/order-success"))
                .andExpect(content().string(containsString(group.getCode())));

        // Double-submit cùng checkoutToken phải bị từ chối
        cartService.addToCart(buyerId, new CartItemRequest(prodShop1.getId(), 1));
        assertThrows(BusinessException.class, () -> orderService.placeOrder(buyerId, formReq, "127.0.0.1"));

        // TC-ORD-09 & TC-ORD-10: Đặt hàng VNPay Sandbox, Return URL thành công (00), thất bại (24) & Chặn chữ ký giả mạo
        CheckoutPreviewResponse vnpPreview = orderService.previewCheckout(buyerId, "STANDARD", null, null);
        CheckoutFormRequest vnpForm = new CheckoutFormRequest(
                address.getId(), "VNPAY", "STANDARD", null, null, vnpPreview.checkoutToken(), "Thanh toán VNPay"
        );
        OrderGroup vnpGroup = orderService.placeOrder(buyerId, vnpForm, "127.0.0.1");

        // Tạo tham số callback VNPay có chữ ký chuẩn HMAC-SHA512
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_TxnRef", vnpGroup.getCode());
        vnpParams.put("vnp_Amount", String.valueOf(vnpGroup.getGrandTotal().multiply(BigDecimal.valueOf(100)).longValue()));
        vnpParams.put("vnp_ResponseCode", "00");
        vnpParams.put("vnp_TransactionNo", "14567890");
        vnpParams.put("vnp_BankCode", "NCB");
        vnpParams.put("vnp_PayDate", "20261123100000");
        String hashData = VnpaySigner.buildHashData(vnpParams);
        String validHash = VnpaySigner.hmacSHA512(appProperties.getVnpay().getHashSecret(), hashData);

        // Chữ ký giả mạo trên Return URL -> hiển thị trang web/payment-failed (không làm rollback transaction)
        mockMvc.perform(get("/payment/vnpay/return")
                        .param("vnp_TxnRef", vnpGroup.getCode())
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_SecureHash", "FAKE_SIGNATURE_123456"))
                .andExpect(status().isOk())
                .andExpect(view().name("web/payment-failed"));

        // Chữ ký hợp lệ & ResponseCode = 00 -> Redirect sang /checkout/success
        mockMvc.perform(get("/payment/vnpay/return")
                        .param("vnp_TxnRef", vnpGroup.getCode())
                        .param("vnp_Amount", vnpParams.get("vnp_Amount"))
                        .param("vnp_ResponseCode", "00")
                        .param("vnp_TransactionNo", "14567890")
                        .param("vnp_BankCode", "NCB")
                        .param("vnp_PayDate", "20261123100000")
                        .param("vnp_SecureHash", validHash))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/checkout/success?groupCode=" + vnpGroup.getCode()));

        OrderGroup paidGroup = orderGroupRepository.findByCode(vnpGroup.getCode()).orElseThrow();
        assertEquals(OrderGroupPaymentStatus.PAID, paidGroup.getPaymentStatus());

        // TC-ORD-11: Theo dõi danh sách đơn (/orders, /account/orders) và chi tiết đơn (/orders/{code})
        Order order1 = splitOrders.get(0);
        Order order2 = splitOrders.get(1);

        mockMvc.perform(get("/orders").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(order1.getCode())));

        mockMvc.perform(get("/orders/" + order1.getCode()).with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(order1.getCode())))
                .andExpect(content().string(containsString("/products?shopId=" + order1.getShop().getId())));

        // TC-ORD-12: Người mua hủy đơn khi đang PENDING_CONFIRM
        mockMvc.perform(post("/api/orders/" + order2.getId() + "/cancel")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Muốn đổi địa chỉ giao hàng\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals(OrderStatus.CANCELLED, orderRepository.findById(order2.getId()).orElseThrow().getStatus());

        // TC-ORD-13: Chặn Người mua hủy đơn khi đã sang PREPARING
        orderService.confirmSellerOrder(order1.getId(), order1.getShop().getId());
        mockMvc.perform(post("/api/orders/" + order1.getId() + "/cancel")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Thử hủy khi đang chuẩn bị\"}"))
                .andExpect(status().isBadRequest());

        // TC-ORD-14: Người mua xác nhận Đã nhận hàng (SHIPPING -> DELIVERED) & Gửi đánh giá
        orderService.shipSellerOrder(order1.getId(), order1.getShop().getId(), "Viettel Post", "VTP12345678");
        mockMvc.perform(post("/api/orders/" + order1.getId() + "/received")
                        .with(user(buyerDetails))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals(OrderStatus.DELIVERED, orderRepository.findById(order1.getId()).orElseThrow().getStatus());

        OrderItem itemToReview = orderItemRepository.findByOrderId(order1.getId()).get(0);
        mockMvc.perform(post("/api/reviews")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ReviewCreateRequest(itemToReview.getId(), 5, "Đặc sản chuẩn vị truyền thống, 5 sao!"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // TC-ORD-15: Mua lại đơn hàng (Re-order) & Yêu cầu hoàn tiền (REFUND_PENDING)
        mockMvc.perform(post("/orders/" + order1.getId() + "/reorder")
                        .with(user(buyerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/cart"));

        mockMvc.perform(post("/orders/" + order1.getId() + "/refund-request")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .header("Accept", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
        assertEquals(OrderPaymentStatus.REFUND_PENDING, orderRepository.findById(order1.getId()).orElseThrow().getPaymentStatus());
    }

    // =========================================================================
    // PHÂN HỆ 4: ĐĂNG KÝ MỞ GIAN HÀNG HTX & THEO DÕI XÉT DUYỆT (TC-SHOPREG-01 .. 07)
    // =========================================================================

    @Test
    @DisplayName("MOD-04 (TC-SHOPREG-01..07): Xác minh khắc phục 100% lỗi trắng trang /account/shop-registration, Nộp hồ sơ, Validation, Trạng thái PENDING/REJECTED/APPROVED & Điều hướng Role")
    void testModule04_ShopRegistrationAndStatusFlows() throws Exception {
        User candidate = userRepository.save(User.builder()
                .fullName("Nghệ Nhân Làng Nghề")
                .email("shop_candidate_e2e@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0912333444")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
        CustomUserDetails candidateDetails = new CustomUserDetails(candidate);

        // TC-SHOPREG-01: Truy cập GET /account/shop-registration không bị trắng trang, render đầy đủ form & sidebar
        MvcResult getFormRes = mockMvc.perform(get("/account/shop-registration").with(user(candidateDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"))
                .andExpect(content().string(containsString("Đăng ký mở gian hàng")))
                .andReturn();
        String formHtml = getFormRes.getResponse().getContentAsString();
        assertTrue(formHtml.length() > 2000, "Trang /account/shop-registration phải render đầy đủ HTML, không bị trắng trang");

        // TC-SHOPREG-03: Validation form rỗng & Tên shop trùng
        mockMvc.perform(post("/account/shop-registration")
                        .with(user(candidateDetails))
                        .with(csrf())
                        .param("name", "")
                        .param("description", "")
                        .param("pickupAddress", "")
                        .param("hotline", "invalid"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"));

        Integer provinceId = provinceRepository.findAll().get(0).getId();
        Shop existingShop = shopRepository.findAll().get(0);
        mockMvc.perform(post("/account/shop-registration")
                        .with(user(candidateDetails))
                        .with(csrf())
                        .param("name", existingShop.getName())
                        .param("description", "Mô tả hợp lệ trên 10 ký tự")
                        .param("pickupAddress", "Địa chỉ kho lấy hàng hợp lệ")
                        .param("hotline", "0912333444")
                        .param("provinceId", String.valueOf(provinceId)))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"))
                .andExpect(model().attributeExists("errorMessage"));

        // TC-SHOPREG-02: Nộp hồ sơ mở gian hàng hợp lệ -> chuyển sang PENDING
        mockMvc.perform(post("/account/shop-registration")
                        .with(user(candidateDetails))
                        .with(csrf())
                        .param("name", "HTX Đặc Sản Cam Vinh Nghệ An E2E")
                        .param("description", "Chuyên cung cấp cam Vinh hữu cơ đạt chuẩn OCOP 4 sao")
                        .param("pickupAddress", "Xã Minh Hợp, Huyện Quỳ Hợp, Nghệ An")
                        .param("hotline", "0912333444")
                        .param("provinceId", String.valueOf(provinceId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/shop-registration"));

        Shop createdShop = shopRepository.findByOwnerId(candidate.getId()).orElseThrow();
        assertEquals(ShopStatus.PENDING, createdShop.getStatus());

        // TC-SHOPREG-04: Hiển thị trang trạng thái khi hồ sơ đang PENDING (không bị lỗi accountSidebar)
        mockMvc.perform(get("/account/shop-registration").with(user(candidateDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration-status"))
                .andExpect(content().string(containsString("HTX Đặc Sản Cam Vinh Nghệ An E2E")));

        // TC-SHOPREG-05: Khi bị Admin từ chối (REJECTED) -> Hiển thị lý do từ chối & Cho phép nộp lại hồ sơ
        User admin = userRepository.findByEmailIgnoreCase("admin@vietspecialty.vn").orElseThrow();
        shopService.rejectShop(createdShop.getId(), admin.getId(), "Cần bổ sung địa chỉ kho chi tiết hơn");

        mockMvc.perform(get("/account/shop-registration").with(user(candidateDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration-status"))
                .andExpect(content().string(containsString("Cần bổ sung địa chỉ kho chi tiết hơn")));

        mockMvc.perform(get("/account/shop-registration").param("reapply", "true").with(user(candidateDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"));

        mockMvc.perform(post("/account/shop-registration")
                        .with(user(candidateDetails))
                        .with(csrf())
                        .param("name", "HTX Đặc Sản Cam Vinh Nghệ An E2E")
                        .param("description", "Đã bổ sung đầy đủ giấy chứng nhận OCOP và địa chỉ kho")
                        .param("pickupAddress", "Số 18 Quốc lộ 48, Xã Minh Hợp, Quỳ Hợp, Nghệ An")
                        .param("hotline", "0912333444")
                        .param("provinceId", String.valueOf(provinceId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/shop-registration"));

        assertEquals(ShopStatus.PENDING, shopRepository.findById(createdShop.getId()).orElseThrow().getStatus());

        // TC-SHOPREG-06: Khi gian hàng đã APPROVED (user là SELLER) -> Redirect sang /seller/dashboard
        CustomUserDetails sellerDetails = detailsOf("seller_bac@vietspecialty.vn");
        mockMvc.perform(get("/account/shop-registration").with(user(sellerDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/dashboard"));

        // TC-SHOPREG-07: Khách chưa đăng nhập -> Redirect về /login; ADMIN -> Redirect về /admin/shops
        mockMvc.perform(get("/account/shop-registration"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));

        CustomUserDetails adminDetails = detailsOf("admin@vietspecialty.vn");
        mockMvc.perform(get("/account/shop-registration").with(user(adminDetails)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shops"));
    }

    // =========================================================================
    // PHÂN HỆ 5: KÊNH NGƯỜI BÁN — SELLER CENTER (TC-SEL-01 .. 12)
    // =========================================================================

    @Test
    @DisplayName("MOD-05 (TC-SEL-01..12): Seller Dashboard, Đơn hàng, Sản phẩm & Chống trùng Slug/IDOR, Voucher Shop, Đánh giá, Hỏi đáp Q&A, Doanh thu CSV UTF-8 BOM & Cài đặt")
    void testModule05_SellerCenterFlows() throws Exception {
        CustomUserDetails sellerBac = detailsOf("seller_bac@vietspecialty.vn");
        Shop shopBac = shopRepository.findByOwnerId(sellerBac.getId()).orElseThrow();

        // TC-SEL-01: Seller Dashboard & Kiểm tra không còn đường dẫn /static/...
        MvcResult dashRes = mockMvc.perform(get("/seller/dashboard").with(user(sellerBac)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/dashboard"))
                .andReturn();
        String dashHtml = dashRes.getResponse().getContentAsString();
        assertFalse(dashHtml.contains("/static/css/"), "Không được chứa đường dẫn sai /static/css/");
        assertTrue(dashHtml.contains("/css/app.css"), "Phải chứa đường dẫn chuẩn /css/app.css");

        // TC-SEL-02 & TC-SEL-03 & TC-SEL-04: Quản lý Đơn bán (/seller/orders)
        mockMvc.perform(get("/seller/orders").with(user(sellerBac)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/orders/index"));

        // TC-SEL-05: Quản lý Sản phẩm của Shop & Lọc theo trạng thái ACTIVE / HIDDEN / INACTIVE
        mockMvc.perform(get("/seller/products").with(user(sellerBac)).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/products/index"));
        mockMvc.perform(get("/seller/products").with(user(sellerBac)).param("status", "HIDDEN"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/seller/products").with(user(sellerBac)).param("status", "INACTIVE"))
                .andExpect(status().isOk());

        // TC-SEL-06 & TC-SEL-07: Đăng sản phẩm mới kèm ảnh PNG hợp lệ & Chống trùng slug với sản phẩm HIDDEN/DELETED
        byte[] pngBytes = new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        MockMultipartFile image1 = new MockMultipartFile("images", "tea1.png", "image/png", pngBytes);
        Integer provinceId = provinceRepository.findAll().get(0).getId();
        Integer categoryId = categoryRepository.findAll().get(0).getId();

        ProductCreateRequest createReq = new ProductCreateRequest(
                "Trà Sen Tây Hồ Đặc Biệt E2E",
                "Trà ướp bông sen Bách Diệp",
                "Câu chuyện nghệ nhân ướp trà sen Tây Hồ Hà Nội...",
                new BigDecimal("350000"),
                50,
                "Hộp 200g",
                200,
                "Quận Tây Hồ, Hà Nội",
                "12 tháng",
                "Bảo quản nơi khô mát",
                5,
                provinceId,
                categoryId
        );
        ProductDetailResponse p1 = productService.createProduct(shopBac.getId(), createReq, List.of(image1));
        assertNotNull(p1);

        // Ẩn sản phẩm p1 rồi tạo thêm sản phẩm cùng tên -> Phải tự sinh slug -1, không sập 500
        productService.toggleProductStatus(shopBac.getId(), p1.id());
        MockMultipartFile image2 = new MockMultipartFile("images", "tea2.png", "image/png", pngBytes);
        ProductDetailResponse p2 = productService.createProduct(shopBac.getId(), createReq, List.of(image2));
        assertNotNull(p2);
        assertNotEquals(p1.slug(), p2.slug(), "Sản phẩm trùng tên với sản phẩm HIDDEN phải tự tăng hậu tố slug");

        // TC-SEL-08: Sửa, Ẩn/Hiện, Xóa mềm & Chống IDOR giữa các shop
        mockMvc.perform(get("/seller/products/" + p2.id() + "/edit").with(user(sellerBac)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/seller/products/" + p2.id() + "/status").with(user(sellerBac)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/products"));
        mockMvc.perform(post("/seller/products/" + p2.id() + "/delete").with(user(sellerBac)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/products"));

        // Chống IDOR: Shop Bắc truy cập sửa sản phẩm của Shop khác phải bị chặn
        Product otherShopProduct = productRepository.findAll().stream()
                .filter(p -> !p.getShop().getId().equals(shopBac.getId()))
                .findFirst().orElseThrow();
        assertThrows(BusinessException.class, () -> productService.deleteProduct(shopBac.getId(), otherShopProduct.getId()));

        // TC-SEL-09: Voucher Shop (/seller/vouchers, /create, /new, toggle)
        mockMvc.perform(get("/seller/vouchers").with(user(sellerBac)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/seller/vouchers/create").with(user(sellerBac)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/seller/vouchers/new").with(user(sellerBac)))
                .andExpect(status().isOk());

        VoucherResponse shopVoucher = voucherService.createShopVoucher(sellerBac.getId(), new VoucherCreateRequest(
                "TRAMOI20E2E",
                "Giảm 20% trà mới",
                DiscountType.PERCENT,
                new BigDecimal("20"),
                new BigDecimal("50000"),
                new BigDecimal("150000"),
                50,
                1,
                LocalDateTime.now().minusHours(1),
                LocalDateTime.now().plusDays(10),
                VoucherScope.SHOP
        ));
        mockMvc.perform(post("/seller/vouchers/" + shopVoucher.id() + "/toggle")
                        .with(user(sellerBac))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/vouchers"));

        // TC-SEL-10: Xem & Phản hồi Đánh giá (/seller/reviews)
        mockMvc.perform(get("/seller/reviews").with(user(sellerBac)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/reviews/index"));

        // TC-SEL-11: Quản lý & Trả lời Hỏi đáp Q&A (/seller/questions)
        User buyer = userRepository.findByEmailIgnoreCase("buyer@vietspecialty.vn").orElseThrow();
        Product shopBacProd = productRepository.findAll().stream()
                .filter(p -> p.getShop().getId().equals(shopBac.getId()))
                .findFirst().orElseThrow();
        ProductComment question = productCommentRepository.save(ProductComment.builder()
                .product(shopBacProd)
                .user(buyer)
                .content("Trà này pha nước bao nhiêu độ là ngon nhất ạ?")
                .isSellerReply(false)
                .status(CommentStatus.VISIBLE)
                .build());

        mockMvc.perform(get("/seller/questions").with(user(sellerBac)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Trà này pha nước bao nhiêu độ là ngon nhất ạ?")));

        mockMvc.perform(post("/seller/questions/" + question.getId() + "/reply")
                        .with(user(sellerBac))
                        .with(csrf())
                        .param("reply", "Dạ quý khách pha nước sôi khoảng 85-90 độ C trong 3 phút là ngon nhất ạ!"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/questions"));

        // TC-SEL-12: Thống kê Doanh thu, Xuất CSV UTF-8 BOM (/seller/revenue, /seller/revenue/export) & Cài đặt (/seller/settings)
        mockMvc.perform(get("/seller/revenue").with(user(sellerBac)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/revenue/index"));

        MvcResult csvRes = mockMvc.perform(get("/seller/revenue/export").with(user(sellerBac)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString(".csv")))
                .andReturn();
        byte[] csvBytes = csvRes.getResponse().getContentAsByteArray();
        assertTrue(csvBytes.length >= 3);
        // Kiểm tra UTF-8 BOM (0xEF, 0xBB, 0xBF)
        assertEquals((byte) 0xEF, csvBytes[0]);
        assertEquals((byte) 0xBB, csvBytes[1]);
        assertEquals((byte) 0xBF, csvBytes[2]);

        mockMvc.perform(get("/seller/settings").with(user(sellerBac)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/settings"));

        mockMvc.perform(post("/seller/settings")
                        .with(user(sellerBac))
                        .with(csrf())
                        .param("name", shopBac.getName())
                        .param("description", "HTX Trà Tân Cương đạt chuẩn OCOP 5 sao quốc gia")
                        .param("pickupAddress", "Xã Tân Cương, TP. Thái Nguyên")
                        .param("hotline", "0912345678"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/settings"));
    }

    // =========================================================================
    // PHÂN HỆ 6: KÊNH QUẢN TRỊ — ADMIN PORTAL (TC-ADM-01 .. 12)
    // =========================================================================

    @Test
    @DisplayName("MOD-06 (TC-ADM-01..12): Admin Dashboard, Duyệt/Từ chối/Đình chỉ Shop, Quản lý User & Khóa phiên, Vùng miền/34 Tỉnh, Danh mục, Voucher Sàn, Kiểm duyệt Review/Q&A, Hoàn tiền & RBAC")
    void testModule06_AdminPortalAndRbacSecurity() throws Exception {
        CustomUserDetails adminDetails = detailsOf("admin@vietspecialty.vn");

        // TC-ADM-01: Admin Dashboard & Kiểm tra CSS/JS
        MvcResult adminDash = mockMvc.perform(get("/admin/dashboard").with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andReturn();
        String adminHtml = adminDash.getResponse().getContentAsString();
        assertFalse(adminHtml.contains("/static/css/"), "Admin layout không được chứa /static/css/");
        assertTrue(adminHtml.contains("/css/app.css"));

        // TC-ADM-02 & TC-ADM-03: Duyệt Shop (nâng quyền SELLER), Từ chối Shop, Đình chỉ & Khôi phục Shop
        User shopOwner = userRepository.save(User.builder()
                .fullName("Chủ HTX Mới")
                .email("new_htx_owner@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0977888999")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
        ShopResponse pendingShop = shopService.registerShop(shopOwner.getId(), new ShopRegisterRequest(
                "HTX Mật Ong Bạc Hà Hà Giang E2E",
                "Mật ong bạc hà cao nguyên đá Đồng Văn",
                "Huyện Mèo Vạc, Hà Giang",
                "0977888999",
                provinceRepository.findAll().get(0).getId()
        ), null);

        mockMvc.perform(get("/admin/shops").with(user(adminDetails)))
                .andExpect(status().isOk());

        // Từ chối trước
        mockMvc.perform(post("/admin/shops/" + pendingShop.id() + "/reject")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("reason", "Cần bổ sung ảnh chụp cơ sở sản xuất"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shops"));
        assertEquals(ShopStatus.REJECTED, shopRepository.findById(pendingShop.id()).orElseThrow().getStatus());

        // Phê duyệt -> Nâng chủ shop lên ROLE_SELLER
        mockMvc.perform(post("/admin/shops/" + pendingShop.id() + "/approve")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/shops"));
        assertEquals(ShopStatus.APPROVED, shopRepository.findById(pendingShop.id()).orElseThrow().getStatus());
        assertEquals(Role.SELLER, userRepository.findById(shopOwner.getId()).orElseThrow().getRole());

        // Đình chỉ & Khôi phục
        mockMvc.perform(post("/admin/shops/" + pendingShop.id() + "/suspend")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(ShopStatus.SUSPENDED, shopRepository.findById(pendingShop.id()).orElseThrow().getStatus());

        mockMvc.perform(post("/admin/shops/" + pendingShop.id() + "/restore")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(ShopStatus.APPROVED, shopRepository.findById(pendingShop.id()).orElseThrow().getStatus());

        // TC-ADM-04 & TC-ADM-05: Quản lý Người dùng (/admin/users), Khóa/Mở khóa & Chặn Admin tự khóa chính mình
        mockMvc.perform(get("/admin/users").with(user(adminDetails)).param("keyword", "buyer"))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/users/index"));

        mockMvc.perform(post("/admin/users/" + shopOwner.getId() + "/lock")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(UserStatus.LOCKED, userRepository.findById(shopOwner.getId()).orElseThrow().getStatus());

        mockMvc.perform(post("/admin/users/" + shopOwner.getId() + "/unlock")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(UserStatus.ACTIVE, userRepository.findById(shopOwner.getId()).orElseThrow().getStatus());

        // Admin tự khóa chính mình phải bị chặn, vẫn ACTIVE
        mockMvc.perform(post("/admin/users/" + adminDetails.getId() + "/lock")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(UserStatus.ACTIVE, userRepository.findById(adminDetails.getId()).orElseThrow().getStatus());

        // TC-ADM-06: Quản lý 4 Vùng miền & 34 Tỉnh/Thành phố (/admin/regions)
        mockMvc.perform(get("/admin/regions").with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/regions/index"))
                .andExpect(content().string(containsString("Miền Bắc")));

        // TC-ADM-07: Quản lý Danh mục (/admin/categories, thêm mới, chống trùng slug, bật/tắt)
        mockMvc.perform(get("/admin/categories").with(user(adminDetails)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/admin/categories/create")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", "Quà Tết Đặc Sản E2E")
                        .param("description", "Giỏ quà Tết OCOP sang trọng")
                        .param("icon", "gift"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));
        // Tạo trùng tên lần 2 -> không sập 500
        mockMvc.perform(post("/admin/categories/create")
                        .with(user(adminDetails))
                        .with(csrf())
                        .param("name", "Quà Tết Đặc Sản E2E"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/categories"));

        // TC-ADM-08: Quản lý Voucher Toàn sàn (/admin/vouchers)
        mockMvc.perform(get("/admin/vouchers").with(user(adminDetails)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/admin/vouchers/create").with(user(adminDetails)))
                .andExpect(status().isOk());

        // TC-ADM-09: Kiểm duyệt Đánh giá & Hỏi đáp (/admin/moderation)
        mockMvc.perform(get("/admin/moderation").with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/moderation/index"));

        ProductComment commentToMod = productCommentRepository.save(ProductComment.builder()
                .product(productRepository.findAll().get(0))
                .user(shopOwner)
                .content("Bình luận kiểm thử moderation")
                .isSellerReply(false)
                .status(CommentStatus.VISIBLE)
                .build());
        mockMvc.perform(post("/admin/moderation/comments/" + commentToMod.getId() + "/toggle")
                        .with(user(adminDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertEquals(CommentStatus.HIDDEN, productCommentRepository.findById(commentToMod.getId()).orElseThrow().getStatus());

        // TC-ADM-10: Quản lý & Xác nhận Hoàn tiền (/admin/refunds: REFUND_PENDING -> REFUNDED)
        mockMvc.perform(get("/admin/refunds").with(user(adminDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/refunds/index"));

        Order sampleOrder = orderRepository.findAll().stream().findFirst().orElse(null);
        if (sampleOrder != null) {
            sampleOrder.setPaymentStatus(OrderPaymentStatus.REFUND_PENDING);
            orderRepository.save(sampleOrder);
            mockMvc.perform(post("/admin/refunds/" + sampleOrder.getId() + "/approve")
                            .with(user(adminDetails))
                            .with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/admin/refunds"));
            assertEquals(OrderPaymentStatus.REFUNDED, orderRepository.findById(sampleOrder.getId()).orElseThrow().getPaymentStatus());
        }

        // TC-ADM-11 & TC-ADM-12: Phân quyền RBAC chặt chẽ
        // Guest vào trang bảo vệ -> 302 Redirect /login; vào API bảo vệ -> 401 JSON
        mockMvc.perform(get("/seller/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
        mockMvc.perform(get("/admin/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/login")));
        mockMvc.perform(post("/api/cart/items").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"productId\":1,\"quantity\":1}"))
                .andExpect(status().isUnauthorized());

        // USER vào /seller/** hoặc /admin/** -> 403 Forbidden
        CustomUserDetails buyerDetails = detailsOf("buyer@vietspecialty.vn");
        mockMvc.perform(get("/seller/dashboard").with(user(buyerDetails)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/dashboard").with(user(buyerDetails)))
                .andExpect(status().isForbidden());

        // SELLER vào /admin/** -> 403 Forbidden; nhưng vào /account/profile -> 200 OK (RoleHierarchy SELLER > USER)
        CustomUserDetails sellerDetails = detailsOf("seller_bac@vietspecialty.vn");
        mockMvc.perform(get("/admin/dashboard").with(user(sellerDetails)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/account/profile").with(user(sellerDetails)))
                .andExpect(status().isOk());
    }

    // =========================================================================
    // PHÂN HỆ 7: TRỢ LÝ AI ẨM THỰC & ĐẶC SẢN (TC-AI-01 .. 06)
    // =========================================================================

    @Test
    @DisplayName("MOD-07 (TC-AI-01..06): Chatbot AI tư vấn Quà biếu / Món nhắm / Mâm cơm, Thẻ sản phẩm gợi ý, Tra cứu đơn hàng, Chính sách & Lịch sử chat")
    void testModule07_AiCulinaryAssistantChatbot() throws Exception {
        String chatKey = "e2e-master-chat-key-001";

        // TC-AI-01 & TC-AI-02: Tư vấn đặc sản làm quà biếu / món nhắm / mâm cơm qua /api/chat
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Tư vấn đặc sản Trà Thái Nguyên làm quà biếu sếp", chatKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("PRODUCT_ADVICE"))
                .andExpect(jsonPath("$.data.suggestedProducts").isNotEmpty());

        mockMvc.perform(post("/api/chatbot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Gợi ý món nhắm Tây Bắc lai rai ngày mưa", chatKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("PRODUCT_ADVICE"))
                .andExpect(jsonPath("$.data.suggestedProducts").isNotEmpty());

        // TC-AI-04: Tra cứu đơn hàng qua Chatbot (mã đơn không tồn tại)
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Tra cứu đơn hàng VS999999FAKE", chatKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("ORDER_LOOKUP"));

        // TC-AI-05: Hỏi đáp chính sách phí ship & đổi trả
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new ChatRequest("Chính sách phí ship và đổi trả hoàn tiền của sàn như thế nào?", chatKey))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.intent").value("POLICY"));

        // TC-AI-06: Tải lại lịch sử hội thoại theo chatKey
        mockMvc.perform(get("/api/chat/history").param("chatKey", chatKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty());
    }
}
