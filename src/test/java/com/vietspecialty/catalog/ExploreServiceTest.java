package com.vietspecialty.catalog;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.ProductImage;
import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.entity.User;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.domain.enums.Role;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.domain.enums.UserStatus;
import com.vietspecialty.dto.response.ExploreProvinceDetailResponse;
import com.vietspecialty.dto.response.ExploreRegionSummaryResponse;
import com.vietspecialty.dto.response.ExploreRegionsResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.repository.ProductImageRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.ExploreService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class ExploreServiceTest {

    @Autowired
    private ExploreService exploreService;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ShopRepository shopRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductImageRepository productImageRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private EntityManager entityManager;

    private User createSeller(String prefix) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return userRepository.save(User.builder()
                .fullName("Seller " + prefix)
                .email(prefix + "_" + uid + "@vietspecialty.vn")
                .passwordHash(passwordEncoder.encode("Password123@"))
                .phone("0901234567")
                .role(Role.SELLER)
                .status(UserStatus.ACTIVE)
                .build());
    }

    private Product createProduct(Shop shop, Category category, Province province, String name,
                                  ProductStatus status, Integer ocopRating, int soldCount) {
        String uid = UUID.randomUUID().toString().substring(0, 8);
        return productRepository.save(Product.builder()
                .shop(shop)
                .category(category)
                .province(province)
                .name(name)
                .slug("sp-" + uid)
                .price(BigDecimal.valueOf(120000))
                .stock(15)
                .unit("Gói 500g")
                .originDetail(province.getName())
                .ocopRating(ocopRating)
                .soldCount(soldCount)
                .avgRating(BigDecimal.valueOf(4.8))
                .reviewCount(5)
                .status(status)
                .build());
    }

    @Test
    @DisplayName("Tổng quan 4 vùng miền & 34 tỉnh thành khớp master data và mã màu gốc")
    void testGetExploreRegionsOverview_returns4RegionsAnd34Provinces() {
        ExploreRegionsResponse overview = exploreService.getExploreRegionsOverview();

        assertNotNull(overview);
        assertEquals(4L, overview.totalRegions());
        assertEquals(34L, overview.totalProvinces());
        assertEquals(4, overview.regions().size());
        assertTrue(overview.totalActiveProducts() > 0);
        assertTrue(overview.totalOcopProducts() > 0);
        assertTrue(overview.totalApprovedShops() >= 3);

        Map<String, ExploreRegionSummaryResponse> bySlug = overview.regions().stream()
                .collect(Collectors.toMap(ExploreRegionSummaryResponse::slug, r -> r));

        assertEquals(List.of("mien-bac", "mien-trung", "tay-nguyen", "mien-nam"),
                overview.regions().stream().map(ExploreRegionSummaryResponse::slug).toList());

        assertEquals("#0F9D8A", bySlug.get("mien-bac").colorHex());
        assertEquals(15L, bySlug.get("mien-bac").provinceCount());

        assertEquals("#E9A21F", bySlug.get("mien-trung").colorHex());
        assertEquals(8L, bySlug.get("mien-trung").provinceCount());

        assertEquals("#8B5E3C", bySlug.get("tay-nguyen").colorHex());
        assertEquals(3L, bySlug.get("tay-nguyen").provinceCount());

        assertEquals("#E8704A", bySlug.get("mien-nam").colorHex());
        assertEquals(8L, bySlug.get("mien-nam").provinceCount());

        long sumProvinces = overview.regions().stream().mapToLong(r -> r.provinces().size()).sum();
        assertEquals(34L, sumProvinces);

        long sumActive = overview.regions().stream().mapToLong(ExploreRegionSummaryResponse::activeProductCount).sum();
        assertEquals(overview.totalActiveProducts(), sumActive);
    }

    @Test
    @DisplayName("Loại trừ sản phẩm HIDDEN/DELETED và gian hàng PENDING/SUSPENDED khỏi thống kê và top đặc sản")
    void testGetProvinceDetail_excludesHiddenDeletedProductsAndUnapprovedShops() {
        Province laiChau = provinceRepository.findBySlugWithRegion("lai-chau").orElseThrow();
        Category category = categoryRepository.findAll().get(0);

        String uid = UUID.randomUUID().toString().substring(0, 8);
        Shop approvedShop = shopRepository.save(Shop.builder()
                .owner(createSeller("app"))
                .name("HTX Lai Châu Chuẩn " + uid)
                .slug("htx-lai-chau-chuan-" + uid)
                .province(laiChau)
                .status(ShopStatus.APPROVED)
                .build());

        Shop pendingShop = shopRepository.save(Shop.builder()
                .owner(createSeller("pend"))
                .name("Shop Chờ Duyệt " + uid)
                .slug("shop-cho-duyet-" + uid)
                .province(laiChau)
                .status(ShopStatus.PENDING)
                .build());

        Shop suspendedShop = shopRepository.save(Shop.builder()
                .owner(createSeller("susp"))
                .name("Shop Tạm Khóa " + uid)
                .slug("shop-tam-khoa-" + uid)
                .province(laiChau)
                .status(ShopStatus.SUSPENDED)
                .build());

        Product visibleOcop = createProduct(approvedShop, category, laiChau, "Trà Shan Tuyết OCOP 4 sao",
                ProductStatus.ACTIVE, 4, 40);
        productImageRepository.save(ProductImage.builder()
                .product(visibleOcop)
                .imageUrl("/images/demo/tra-lai-chau.jpg")
                .sortOrder(0)
                .isPrimary(true)
                .build());

        // Active product with ocopRating = 2 (below OCOP 3-star threshold)
        createProduct(approvedShop, category, laiChau, "Mật ong rừng Lai Châu",
                ProductStatus.ACTIVE, 2, 10);

        // Must be excluded
        createProduct(approvedShop, category, laiChau, "Sản phẩm ẩn", ProductStatus.HIDDEN, 5, 99);
        createProduct(approvedShop, category, laiChau, "Sản phẩm đã xóa", ProductStatus.DELETED, 5, 99);
        createProduct(pendingShop, category, laiChau, "Sản phẩm shop pending", ProductStatus.ACTIVE, 4, 99);
        createProduct(suspendedShop, category, laiChau, "Sản phẩm shop suspended", ProductStatus.ACTIVE, 4, 99);

        entityManager.flush();
        entityManager.clear();

        ExploreProvinceDetailResponse detail = exploreService.getProvinceDetail("lai-chau", 6);

        assertEquals("lai-chau", detail.slug());
        assertEquals(2L, detail.activeProductCount());
        assertEquals(1L, detail.ocopProductCount());
        assertEquals(1L, detail.approvedShopCount());
        assertEquals(1, detail.approvedShops().size());
        assertEquals(approvedShop.getSlug(), detail.approvedShops().get(0).slug());
        assertEquals(2L, detail.approvedShops().get(0).activeProductCount());

        assertEquals(2, detail.topSpecialties().size());
        assertEquals(visibleOcop.getId(), detail.topSpecialties().get(0).id());
        assertEquals("/images/demo/tra-lai-chau.jpg", detail.topSpecialties().get(0).primaryImageUrl());

        ExploreRegionsResponse overview = exploreService.getExploreRegionsOverview();
        var laiChauSummary = overview.regions().stream()
                .flatMap(r -> r.provinces().stream())
                .filter(p -> p.slug().equals("lai-chau"))
                .findFirst()
                .orElseThrow();
        assertEquals(2L, laiChauSummary.activeProductCount());
        assertEquals(1L, laiChauSummary.ocopProductCount());
        assertEquals(1L, laiChauSummary.approvedShopCount());
    }

    @Test
    @DisplayName("Tỉnh chưa có sản phẩm trả về bộ đếm 0 và danh sách rỗng (không null)")
    void testGetProvinceDetail_emptyProvinceReturnsZeroCountsAndEmptyLists() {
        ExploreProvinceDetailResponse detail = exploreService.getProvinceDetail("lai-chau", 6);

        assertEquals("lai-chau", detail.slug());
        assertEquals("/products?provinceSlug=lai-chau", detail.productsFilterUrl());
        assertEquals(0L, detail.activeProductCount());
        assertEquals(0L, detail.ocopProductCount());
        assertEquals(0L, detail.approvedShopCount());
        assertNotNull(detail.approvedShops());
        assertTrue(detail.approvedShops().isEmpty());
        assertNotNull(detail.topSpecialties());
        assertTrue(detail.topSpecialties().isEmpty());
    }

    @Test
    @DisplayName("Slug tỉnh không tồn tại ném BusinessException với ErrorCode.PROVINCE_NOT_FOUND (404)")
    void testGetProvinceDetail_unknownSlugThrowsProvinceNotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> exploreService.getProvinceDetail("tinh-khong-ton-tai-999", 6));
        assertEquals(ErrorCode.PROVINCE_NOT_FOUND, ex.getErrorCode());
        assertEquals(HttpStatus.NOT_FOUND, ex.getErrorCode().getHttpStatus());

        BusinessException blankEx = assertThrows(BusinessException.class,
                () -> exploreService.getProvinceDetail("   ", 6));
        assertEquals(ErrorCode.PROVINCE_NOT_FOUND, blankEx.getErrorCode());
    }
}
