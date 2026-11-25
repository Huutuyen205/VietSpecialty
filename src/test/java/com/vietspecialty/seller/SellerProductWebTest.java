package com.vietspecialty.seller;

import com.vietspecialty.domain.entity.*;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.repository.*;
import com.vietspecialty.security.CustomUserDetails;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Transactional
class SellerProductWebTest {

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
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private StorageService storageService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private final List<String> uploadedImageUrls = new ArrayList<>();

    @AfterEach
    void cleanupUploadedFiles() {
        for (String url : uploadedImageUrls) {
            storageService.deleteImage(url);
        }
        uploadedImageUrls.clear();
    }

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
                .addressDetail("123 Đường Đặc Sản")
                .phone("0912345678")
                .status(ShopStatus.APPROVED)
                .build());
    }

    private Product createProduct(Shop shop, String name) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        Province province = provinceRepository.findAll().get(0);
        Category category = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0);
        return productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name(name)
                .slug("sp-" + uid)
                .shortDescription("Mô tả ngắn")
                .description("Mô tả chi tiết đặc sản vùng miền")
                .price(new BigDecimal("150000"))
                .stock(20)
                .unit("Hộp 500g")
                .weightGram(500)
                .originDetail("Thái Nguyên")
                .status(ProductStatus.ACTIVE)
                .build());
    }

    private Set<Path> snapshotProductUploadDir() throws Exception {
        Path dir = Paths.get("./uploads/products").toAbsolutePath().normalize();
        if (!Files.exists(dir)) {
            return Set.of();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream.collect(Collectors.toSet());
        }
    }

    private Path toDiskPath(String imageUrl) {
        String relative = imageUrl.replaceFirst("^/uploads/", "");
        return Paths.get("./uploads", relative).toAbsolutePath().normalize();
    }

    @Test
    @DisplayName("GET /seller/products/create và /new hiển thị form tạo sản phẩm hợp lệ; ROLE_USER bị chặn 403")
    void testCreateFormAndRbac() throws Exception {
        User seller = createUser("prod_form_s", Role.SELLER);
        createApprovedShop(seller, "prodform");
        User buyer = createUser("prod_form_b", Role.USER);
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);
        CustomUserDetails buyerDetails = new CustomUserDetails(buyer);

        mockMvc.perform(get("/seller/products/create").with(user(sellerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/products/form"))
                .andExpect(model().attribute("isEdit", false));

        mockMvc.perform(get("/seller/products/new").with(user(sellerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/products/form"))
                .andExpect(model().attribute("isEdit", false));

        mockMvc.perform(get("/seller/products").with(user(buyerDetails)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /seller/products/create với ảnh PNG hợp lệ tạo sản phẩm, lưu file disk và dọn sạch sau test")
    void testCreateProductWithValidMultipartImageAndCleanup() throws Exception {
        User seller = createUser("prod_create_s", Role.SELLER);
        Shop shop = createApprovedShop(seller, "prodcreate");
        Integer provinceId = provinceRepository.findAll().get(0).getId();
        Integer categoryId = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0).getId();
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);
        MockMultipartFile pngFile = new MockMultipartFile("images", "tra-shan.png", "image/png", VALID_PNG_BYTES);
        String uniqueName = "Trà Shan Tuyết M2 " + UUID.randomUUID().toString().substring(0, 6);

        mockMvc.perform(multipart("/seller/products/create")
                        .file(pngFile)
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("name", uniqueName)
                        .param("shortDescription", "Trà cổ thụ Hà Giang")
                        .param("description", "Đặc sản trà cổ thụ hái tay từ đỉnh Tây Côn Lĩnh")
                        .param("price", "250000")
                        .param("stock", "15")
                        .param("unit", "Hộp 250g")
                        .param("weightGram", "250")
                        .param("originDetail", "Hoàng Su Phì, Hà Giang")
                        .param("ocopRating", "4")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/products"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        List<Product> products = productRepository.findByShopIdAndStatusNot(
                shop.getId(), ProductStatus.DELETED, org.springframework.data.domain.PageRequest.of(0, 10)).getContent();
        Product saved = products.stream()
                .filter(p -> uniqueName.equals(p.getName()))
                .findFirst()
                .orElseThrow();

        assertEquals(ProductStatus.ACTIVE, saved.getStatus());
        assertFalse(saved.getImages().isEmpty());
        String imageUrl = saved.getImages().get(0).getImageUrl();
        assertTrue(imageUrl.startsWith("/uploads/products/"));
        uploadedImageUrls.add(imageUrl);

        Path diskPath = toDiskPath(imageUrl);
        try {
            assertTrue(Files.exists(diskPath), "File ảnh sản phẩm phải tồn tại trên disk");
        } finally {
            storageService.deleteImage(imageUrl);
            Files.deleteIfExists(diskPath);
            uploadedImageUrls.remove(imageUrl);
        }
        assertFalse(Files.exists(diskPath), "File ảnh sản phẩm phải được dọn sạch sau test");
    }

    @Test
    @DisplayName("POST /seller/products/create kiểm tra lỗi Bean Validation và thiếu ảnh bắt buộc")
    void testCreateProductValidationErrorsAndMissingImage() throws Exception {
        User seller = createUser("prod_val_s", Role.SELLER);
        Shop shop = createApprovedShop(seller, "prodval");
        Integer provinceId = provinceRepository.findAll().get(0).getId();
        Integer categoryId = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0).getId();
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);

        // 1. Bean Validation errors: short name & price < 1000
        mockMvc.perform(multipart("/seller/products/create")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("name", "Abc")
                        .param("description", "Mô tả hợp lệ")
                        .param("price", "500")
                        .param("stock", "5")
                        .param("unit", "Gói")
                        .param("originDetail", "Hà Nội")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/products/form"))
                .andExpect(model().attributeHasFieldErrors("productRequest", "name", "price"));

        // 2. Valid fields but missing images -> BusinessException (PROD_002) caught into errorMessage
        mockMvc.perform(multipart("/seller/products/new")
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("name", "Bánh Đậu Xanh Hải Dương")
                        .param("description", "Đặc sản bánh đậu xanh truyền thống")
                        .param("price", "50000")
                        .param("stock", "10")
                        .param("unit", "Hộp 300g")
                        .param("originDetail", "Hải Dương")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/products/form"))
                .andExpect(model().attributeExists("errorMessage"));

        assertEquals(0, productRepository.countByShopId(shop.getId()));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("MagicByteVerifier chặn file .exe giả mạo .jpg và script text giả mạo .png khi tạo sản phẩm")
    void testMagicByteVerifierRejectsDisguisedFilesOnCreate() throws Exception {
        User seller = null;
        Shop shop = null;
        try {
            seller = createUser("prod_magic_s", Role.SELLER);
            shop = createApprovedShop(seller, "prodmagic");
            Integer provinceId = provinceRepository.findAll().get(0).getId();
            Integer categoryId = categoryRepository.findByIsActiveTrueOrderBySortOrderAsc().get(0).getId();

            CustomUserDetails sellerDetails = new CustomUserDetails(seller);
            Set<Path> beforeFiles = snapshotProductUploadDir();

            // 1. Disguised .exe masquerading as .jpg
            byte[] fakeExe = "MZ\u0090\u0000FAKE_PE_HEADER_PAYLOAD".getBytes(StandardCharsets.ISO_8859_1);
            MockMultipartFile disguisedExe = new MockMultipartFile("images", "malware.jpg", "image/jpeg", fakeExe);

            mockMvc.perform(multipart("/seller/products/create")
                            .file(disguisedExe)
                            .with(user(sellerDetails))
                            .with(csrf())
                            .param("name", "Sản phẩm giả mạo EXE")
                            .param("description", "Mô tả kiểm thử bảo mật upload")
                            .param("price", "120000")
                            .param("stock", "10")
                            .param("unit", "Hộp")
                            .param("originDetail", "Hà Nội")
                            .param("provinceId", String.valueOf(provinceId))
                            .param("categoryId", String.valueOf(categoryId)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("seller/products/form"))
                    .andExpect(model().attribute("errorMessage", containsString("không phải là hình ảnh hợp lệ")));

            // 2. Disguised PHP/text masquerading as .png
            MockMultipartFile disguisedText = new MockMultipartFile(
                    "images", "shell.png", "image/png", "<?php echo 'hacked'; ?>".getBytes(StandardCharsets.UTF_8));

            mockMvc.perform(multipart("/seller/products/create")
                            .file(disguisedText)
                            .with(user(sellerDetails))
                            .with(csrf())
                            .param("name", "Sản phẩm giả mạo PNG")
                            .param("description", "Mô tả kiểm thử bảo mật upload")
                            .param("price", "120000")
                            .param("stock", "10")
                            .param("unit", "Hộp")
                            .param("originDetail", "Hà Nội")
                            .param("provinceId", String.valueOf(provinceId))
                            .param("categoryId", String.valueOf(categoryId)))
                    .andExpect(status().isOk())
                    .andExpect(view().name("seller/products/form"))
                    .andExpect(model().attribute("errorMessage", containsString("không phải là hình ảnh hợp lệ")));

            Set<Path> afterFiles = snapshotProductUploadDir();
            assertEquals(beforeFiles, afterFiles, "Không được ghi file giả mạo xuống đĩa");
            assertEquals(0, productRepository.countByShopId(shop.getId()));
        } finally {
            if (shop != null && shop.getId() != null) {
                shopRepository.deleteById(shop.getId());
            }
            if (seller != null && seller.getId() != null) {
                userRepository.deleteById(seller.getId());
            }
        }
    }

    @Test
    @DisplayName("GET/POST /seller/products/{id}/edit với newImages, /toggle, /status và /delete hoạt động đúng cho chủ shop")
    void testEditToggleStatusAndDeleteByOwner() throws Exception {
        User seller = createUser("prod_edit_s", Role.SELLER);
        Shop shop = createApprovedShop(seller, "prodedit");
        Product product = createProduct(shop, "Mật Ong Rừng U Minh Cũ");
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerDetails = new CustomUserDetails(seller);
        Integer provinceId = product.getProvince().getId();
        Integer categoryId = product.getCategory().getId();

        // GET edit
        mockMvc.perform(get("/seller/products/" + product.getId() + "/edit").with(user(sellerDetails)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/products/form"))
                .andExpect(model().attribute("isEdit", true));

        // POST edit with newImages
        MockMultipartFile newPng = new MockMultipartFile("newImages", "mat-ong.png", "image/png", VALID_PNG_BYTES);
        mockMvc.perform(multipart("/seller/products/" + product.getId() + "/edit")
                        .file(newPng)
                        .with(user(sellerDetails))
                        .with(csrf())
                        .param("name", "Mật Ong Rừng U Minh Thượng Hạng")
                        .param("shortDescription", "Mật ong nguyên chất")
                        .param("description", "Thu hoạch tự nhiên từ rừng tràm U Minh")
                        .param("price", "320000")
                        .param("stock", "30")
                        .param("unit", "Chai 500ml")
                        .param("weightGram", "600")
                        .param("originDetail", "U Minh, Cà Mau")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/products"))
                .andExpect(flash().attributeExists("successMessage"));

        entityManager.flush();
        entityManager.clear();

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertEquals("Mật Ong Rừng U Minh Thượng Hạng", updated.getName());
        assertEquals(0, new BigDecimal("320000").compareTo(updated.getPrice()));
        assertEquals(30, updated.getStock());
        assertFalse(updated.getImages().isEmpty());
        String newImgUrl = updated.getImages().get(0).getImageUrl();
        uploadedImageUrls.add(newImgUrl);
        Path diskPath = toDiskPath(newImgUrl);
        try {
            assertTrue(Files.exists(diskPath));
        } finally {
            storageService.deleteImage(newImgUrl);
            Files.deleteIfExists(diskPath);
            uploadedImageUrls.remove(newImgUrl);
        }

        // Toggle ACTIVE -> HIDDEN via /toggle
        mockMvc.perform(post("/seller/products/" + product.getId() + "/toggle")
                        .with(user(sellerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/products"));
        entityManager.flush();
        entityManager.clear();
        assertEquals(ProductStatus.HIDDEN, productRepository.findById(product.getId()).orElseThrow().getStatus());

        // Toggle HIDDEN -> ACTIVE via /status
        mockMvc.perform(post("/seller/products/" + product.getId() + "/status")
                        .with(user(sellerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/products"));
        entityManager.flush();
        entityManager.clear();
        assertEquals(ProductStatus.ACTIVE, productRepository.findById(product.getId()).orElseThrow().getStatus());

        // Soft delete via /delete
        mockMvc.perform(post("/seller/products/" + product.getId() + "/delete")
                        .with(user(sellerDetails))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/seller/products"));
        entityManager.flush();
        entityManager.clear();
        assertEquals(ProductStatus.DELETED, productRepository.findById(product.getId()).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("Chống IDOR: Seller B không thể GET edit, POST edit, toggle hoặc delete sản phẩm của Seller A")
    void testCrossSellerProductIdorProtection() throws Exception {
        User sellerA = createUser("prod_idor_a", Role.SELLER);
        Shop shopA = createApprovedShop(sellerA, "idora");
        Product productA = createProduct(shopA, "Đặc Sản Bảo Vệ IDOR");

        User sellerB = createUser("prod_idor_b", Role.SELLER);
        createApprovedShop(sellerB, "idorb");
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails sellerBDetails = new CustomUserDetails(sellerB);
        Integer provinceId = productA.getProvince().getId();
        Integer categoryId = productA.getCategory().getId();

        // GET /{id}/edit -> 400 error/500 AUTH_002
        mockMvc.perform(get("/seller/products/" + productA.getId() + "/edit").with(user(sellerBDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "AUTH_002"));

        // POST /{id}/edit -> 200 seller/products/form with errorMessage
        mockMvc.perform(multipart("/seller/products/" + productA.getId() + "/edit")
                        .with(user(sellerBDetails))
                        .with(csrf())
                        .param("name", "Tên Bị Sửa Trộm")
                        .param("description", "Mô tả")
                        .param("price", "999000")
                        .param("stock", "1")
                        .param("unit", "Hộp")
                        .param("originDetail", "Hà Nội")
                        .param("provinceId", String.valueOf(provinceId))
                        .param("categoryId", String.valueOf(categoryId)))
                .andExpect(status().isOk())
                .andExpect(view().name("seller/products/form"))
                .andExpect(model().attributeExists("errorMessage"));

        // POST /{id}/toggle -> 400 error/500 PROD_001
        mockMvc.perform(post("/seller/products/" + productA.getId() + "/toggle")
                        .with(user(sellerBDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "PROD_001"));

        // POST /{id}/delete -> 400 error/500 PROD_001
        mockMvc.perform(post("/seller/products/" + productA.getId() + "/delete")
                        .with(user(sellerBDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "PROD_001"));

        Product unchanged = productRepository.findById(productA.getId()).orElseThrow();
        assertEquals("Đặc Sản Bảo Vệ IDOR", unchanged.getName());
        assertEquals(ProductStatus.ACTIVE, unchanged.getStatus());
    }

    @Test
    @DisplayName("DEF-12: Shop SUSPENDED hoặc PENDING bị chặn với SHOP_004 trên tất cả endpoint /seller/products")
    void testSuspendedAndPendingShopBlockedOnSellerProducts() throws Exception {
        Province province = provinceRepository.findAll().get(0);

        User suspendedSeller = createUser("prod_susp_s", Role.SELLER);
        Shop suspendedShop = shopRepository.save(Shop.builder()
                .name("Shop Suspended " + UUID.randomUUID().toString().substring(0, 6))
                .slug("shop-susp-" + UUID.randomUUID().toString().substring(0, 6))
                .owner(suspendedSeller)
                .province(province)
                .addressDetail("123 Đường")
                .phone("0912345678")
                .status(ShopStatus.SUSPENDED)
                .build());
        Product prod = createProduct(suspendedShop, "Sản Phẩm Shop Đình Chỉ");

        User pendingSeller = createUser("prod_pend_s", Role.SELLER);
        shopRepository.save(Shop.builder()
                .name("Shop Pending " + UUID.randomUUID().toString().substring(0, 6))
                .slug("shop-pend-" + UUID.randomUUID().toString().substring(0, 6))
                .owner(pendingSeller)
                .province(province)
                .addressDetail("123 Đường")
                .phone("0912345678")
                .status(ShopStatus.PENDING)
                .build());
        entityManager.flush();
        entityManager.clear();

        CustomUserDetails suspendedDetails = new CustomUserDetails(suspendedSeller);
        CustomUserDetails pendingDetails = new CustomUserDetails(pendingSeller);

        mockMvc.perform(get("/seller/products").with(user(suspendedDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "SHOP_004"));

        mockMvc.perform(get("/seller/products/create").with(user(suspendedDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "SHOP_004"));

        mockMvc.perform(post("/seller/products/" + prod.getId() + "/toggle")
                        .with(user(suspendedDetails))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "SHOP_004"));

        mockMvc.perform(get("/seller/products").with(user(pendingDetails)))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/500"))
                .andExpect(model().attribute("errorCode", "SHOP_004"));
    }
}
