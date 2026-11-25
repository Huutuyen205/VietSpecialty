package com.vietspecialty.account;

import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.ShopService;
import com.vietspecialty.service.StorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class ShopRegistrationWebTest {

    private static final byte[] VALID_PNG_BYTES = new byte[]{
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ShopService shopService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private final List<String> uploadedLogoUrls = new ArrayList<>();

    @AfterEach
    void cleanupUploadedLogos() {
        for (String url : uploadedLogoUrls) {
            storageService.deleteImage(url);
        }
        uploadedLogoUrls.clear();
    }

    private User createFreshBuyer(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .fullName("Buyer " + prefix)
                .email(prefix + "_" + uid + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0912345678")
                .role(Role.USER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Set<Path> snapshotShopUploadDir() throws Exception {
        Path dir = Paths.get("./uploads/shops").toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            return Set.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.collect(Collectors.toSet());
        }
    }

    private Path toDiskPath(String logoUrl) {
        String relative = logoUrl.replaceFirst("^/uploads/", "");
        return Paths.get("./uploads", relative).toAbsolutePath().normalize();
    }

    @Test
    @DisplayName("GET/POST /account/shop-registration tải lên logo PNG hợp lệ, chuyển PENDING, hiển thị trang trạng thái và dọn sạch đĩa")
    void testShopRegistrationWithMultipartLogoAndCleanup() throws Exception {
        User buyer = createFreshBuyer("shopreg_ok");
        Province province = provinceRepository.findAll().get(0);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);
        String shopName = "HTX Đặc Sản Logo M2 " + UUID.randomUUID().toString().substring(0, 6);

        // Initial GET form
        mockMvc.perform(get("/account/shop-registration").with(user(buyerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"));

        // Valid multipart POST with "logo"
        MockMultipartFile logoFile = new MockMultipartFile("logo", "shop-logo.png", "image/png", VALID_PNG_BYTES);
        mockMvc.perform(multipart("/account/shop-registration")
                        .file(logoFile)
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("name", shopName)
                        .param("description", "Cung cấp đặc sản chính gốc đạt chuẩn OCOP")
                        .param("pickupAddress", "Số 18 Đường Làng Nghề")
                        .param("hotline", "0912345678")
                        .param("provinceId", String.valueOf(province.getId())))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account/shop-registration"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Shop savedShop = shopRepository.findByOwnerId(buyer.getId()).orElseThrow();
        assertEquals(ShopStatus.PENDING, savedShop.getStatus());
        assertEquals(shopName, savedShop.getName());
        assertNotNull(savedShop.getLogoUrl());
        assertTrue(savedShop.getLogoUrl().startsWith("/uploads/shops/"));
        uploadedLogoUrls.add(savedShop.getLogoUrl());

        Path diskPath = toDiskPath(savedShop.getLogoUrl());
        try {
            assertTrue(Files.exists(diskPath), "Logo gian hàng phải được lưu trên disk");

            // Subsequent GET renders pending status page
            mockMvc.perform(get("/account/shop-registration").with(user(buyerDetails)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("account/shop-registration-status"))
                    .andExpect(content().string(containsString(shopName)));
        } finally {
            storageService.deleteImage(savedShop.getLogoUrl());
            Files.deleteIfExists(diskPath);
            uploadedLogoUrls.remove(savedShop.getLogoUrl());
        }
        assertFalse(Files.exists(diskPath), "Logo test phải được xóa khỏi disk sau test");
    }

    @Test
    @DisplayName("Chặn đăng ký gian hàng thứ 2 khi tài khoản đang có gian hàng PENDING")
    void testDuplicateShopGuardWhilePending() throws Exception {
        User buyer = createFreshBuyer("shopreg_dup");
        Province province = provinceRepository.findAll().get(0);
        String uid = UUID.randomUUID().toString().substring(0, 6);
        String originalName = "HTX Đang Chờ Duyệt " + uid;

        shopRepository.save(Shop.builder()
                .name(originalName)
                .slug("htx-pending-" + uid)
                .owner(buyer)
                .province(province)
                .addressDetail("123 Đường Chờ")
                .phone("0912345678")
                .status(ShopStatus.PENDING)
                .build());
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        mockMvc.perform(multipart("/account/shop-registration")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("name", "HTX Thứ Hai " + uid)
                        .param("description", "Thử tạo shop thứ 2")
                        .param("pickupAddress", "456 Đường Mới")
                        .param("hotline", "0987654321")
                        .param("provinceId", String.valueOf(province.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"))
                .andExpect(model().attribute("errorMessage", containsString("Mỗi tài khoản chỉ được phép sở hữu một gian hàng")));

        Shop stillOriginal = shopRepository.findByOwnerId(buyer.getId()).orElseThrow();
        assertEquals(originalName, stillOriginal.getName());
        assertEquals(ShopStatus.PENDING, stillOriginal.getStatus());
    }

    @Test
    @DisplayName("MagicByteVerifier chặn file giả mạo ảnh khi đăng ký shop và không tạo shop/file đĩa")
    void testMagicByteVerifierRejectsDisguisedLogo() throws Exception {
        User buyer = createFreshBuyer("shopreg_magic");
        Province province = provinceRepository.findAll().get(0);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);
        Set<Path> beforeFiles = snapshotShopUploadDir();

        byte[] fakeExe = "MZ\u0090\u0000FAKE_EXE_LOGO".getBytes(StandardCharsets.ISO_8859_1);
        MockMultipartFile disguisedLogo = new MockMultipartFile("logo", "shell.png", "image/png", fakeExe);

        mockMvc.perform(multipart("/account/shop-registration")
                        .file(disguisedLogo)
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("name", "HTX Logo Giả Mạo " + UUID.randomUUID().toString().substring(0, 6))
                        .param("description", "Kiểm thử upload logo giả mạo")
                        .param("pickupAddress", "100 Đường Kiểm Thử")
                        .param("hotline", "0912345678")
                        .param("provinceId", String.valueOf(province.getId())))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"))
                .andExpect(model().attribute("errorMessage", containsString("hình ảnh hợp lệ")));

        assertTrue(shopRepository.findByOwnerId(buyer.getId()).isEmpty(),
                "Không được tạo gian hàng khi logo vi phạm MagicByteVerifier");
        Set<Path> afterFiles = snapshotShopUploadDir();
        assertEquals(beforeFiles, afterFiles, "Không được lưu file giả mạo vào ./uploads/shops/");
    }

    @Test
    @DisplayName("POST /account/shop-registration kiểm tra lỗi Bean Validation khi thiếu tên shop và tỉnh/thành")
    void testShopRegistrationBeanValidationErrors() throws Exception {
        User buyer = createFreshBuyer("shopreg_val");
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        mockMvc.perform(post("/account/shop-registration")
                        .with(user(buyerDetails))
                        .with(csrf())
                        .param("name", "")
                        .param("pickupAddress", "123 Đường Test")
                        .param("hotline", "0912345678"))
                .andExpect(status().isOk())
                .andExpect(view().name("account/shop-registration"))
                .andExpect(model().attributeHasFieldErrors("shopRequest", "name", "provinceId"));

        assertTrue(shopRepository.findByOwnerId(buyer.getId()).isEmpty());
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("DEF-05 (Non-Transactional): SĐT hotline sai định dạng bị Bean Validation chặn; trùng tên/file giả mạo/resubmit không gây UnexpectedRollbackException 500")
    void testShopRegistrationNonTransactionalRollbackAndPhoneValidation() throws Exception {
        User buyer = null;
        Shop createdShop = null;
        try {
            buyer = createFreshBuyer("shopreg_def05");
            Province province = provinceRepository.findAll().get(0);
            User admin = userRepository.findByEmailIgnoreCase("admin@vietspecialty.vn").orElseThrow();
            Shop existingShop = shopRepository.findAll().stream().findFirst().orElseThrow();
            CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

            // 1. Invalid hotline format -> Bean Validation rejects hotline field (including literal '|' in prefix)
            for (String invalidHotline : List.of("12345", "0|12345678", "+84|12345678")) {
                mockMvc.perform(multipart("/account/shop-registration")
                                .with(user(buyerDetails))
                                .with(csrf())
                                .param("name", "HTX SĐT Sai " + UUID.randomUUID().toString().substring(0, 6))
                                .param("description", "Mô tả hợp lệ")
                                .param("pickupAddress", "123 Đường Test")
                                .param("hotline", invalidHotline)
                                .param("provinceId", String.valueOf(province.getId())))
                        .andExpect(status().isOk())
                        .andExpect(view().name("account/shop-registration"))
                        .andExpect(model().attributeHasFieldErrors("shopRequest", "hotline"));
            }

            // 2. Duplicate shop name -> 200 OK with errorMessage (NO UnexpectedRollbackException 500)
            mockMvc.perform(multipart("/account/shop-registration")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .param("name", existingShop.getName())
                            .param("description", "Mô tả hợp lệ")
                            .param("pickupAddress", "123 Đường Test")
                            .param("hotline", "0912345678")
                            .param("provinceId", String.valueOf(province.getId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("account/shop-registration"))
                    .andExpect(model().attributeExists("errorMessage"));

            // 3. Disguised file -> 200 OK with errorMessage (NO UnexpectedRollbackException 500)
            byte[] fakeExe = "MZ\u0090\u0000FAKE_EXE_LOGO".getBytes(StandardCharsets.ISO_8859_1);
            MockMultipartFile badLogo = new MockMultipartFile("logo", "bad.png", "image/png", fakeExe);
            mockMvc.perform(multipart("/account/shop-registration")
                            .file(badLogo)
                            .with(user(buyerDetails))
                            .with(csrf())
                            .param("name", "HTX Logo Sai " + UUID.randomUUID().toString().substring(0, 6))
                            .param("description", "Mô tả hợp lệ")
                            .param("pickupAddress", "123 Đường Test")
                            .param("hotline", "0912345678")
                            .param("provinceId", String.valueOf(province.getId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("account/shop-registration"))
                    .andExpect(model().attribute("errorMessage", containsString("hình ảnh hợp lệ")));

            // 4. Valid submit -> PENDING, reject -> REJECTED, resubmit duplicate fails gracefully, resubmit new name updates slug
            String uid = UUID.randomUUID().toString().substring(0, 6);
            String initialName = "HTX Gốc DEF05 " + uid;
            mockMvc.perform(multipart("/account/shop-registration")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .param("name", initialName)
                            .param("description", "Mô tả ban đầu")
                            .param("pickupAddress", "123 Đường Test")
                            .param("hotline", "0912345678")
                            .param("provinceId", String.valueOf(province.getId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/account/shop-registration"));

            createdShop = shopRepository.findByOwnerId(buyer.getId()).orElseThrow();
            String originalSlug = createdShop.getSlug();
            shopService.rejectShop(createdShop.getId(), admin.getId(), "Thiếu thông tin");

            // Resubmit with duplicate existing shop name -> 200 OK with errorMessage (NO 500)
            mockMvc.perform(multipart("/account/shop-registration")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .param("name", existingShop.getName())
                            .param("description", "Bổ sung mô tả")
                            .param("pickupAddress", "456 Đường Mới")
                            .param("hotline", "0987654321")
                            .param("provinceId", String.valueOf(province.getId())))
                    .andExpect(status().isOk())
                    .andExpect(view().name("account/shop-registration"))
                    .andExpect(model().attributeExists("errorMessage"));

            // Resubmit with new valid name -> 302 redirect, status=PENDING, rejectReason=null, slug regenerated
            String renamed = "HTX Đổi Tên DEF05 " + uid;
            mockMvc.perform(multipart("/account/shop-registration")
                            .with(user(buyerDetails))
                            .with(csrf())
                            .param("name", renamed)
                            .param("description", "Bổ sung mô tả đầy đủ")
                            .param("pickupAddress", "456 Đường Mới")
                            .param("hotline", "0987654321")
                            .param("provinceId", String.valueOf(province.getId())))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/account/shop-registration"))
                    .andExpect(flash().attributeExists("successMessage"));

            Shop updated = shopRepository.findById(createdShop.getId()).orElseThrow();
            assertEquals(ShopStatus.PENDING, updated.getStatus());
            assertNull(updated.getRejectReason());
            assertEquals(renamed, updated.getName());
            assertNotEquals(originalSlug, updated.getSlug());
        } finally {
            if (createdShop != null && createdShop.getId() != null) {
                shopRepository.deleteById(createdShop.getId());
            } else if (buyer != null && buyer.getId() != null) {
                shopRepository.findByOwnerId(buyer.getId())
                        .ifPresent(s -> shopRepository.deleteById(s.getId()));
            }
            if (buyer != null && buyer.getId() != null) {
                userRepository.deleteById(buyer.getId());
            }
        }
    }
}
