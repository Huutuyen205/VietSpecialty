package com.vietspecialty.catalog;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.domain.entity.Region;
import com.vietspecialty.service.CatalogService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class CatalogServiceTest {

    @Autowired
    private CatalogService catalogService;

    @Test
    @DisplayName("Lấy danh sách 4 vùng miền từ Flyway master data thành công")
    void testGetAllRegions() {
        List<Region> regions = catalogService.getAllRegions();
        assertNotNull(regions);
        assertEquals(4, regions.size(), "Hệ thống phải có đủ 4 vùng miền");
    }

    @Test
    @DisplayName("Lấy danh sách danh mục hoạt động từ Flyway master data thành công")
    void testGetAllCategories() {
        List<Category> categories = catalogService.getAllActiveCategories();
        assertNotNull(categories);
        assertEquals(8, categories.size(), "Hệ thống phải có đủ 8 danh mục đặc sản");
    }

    @Test
    @DisplayName("Tìm vùng miền theo slug hợp lệ")
    void testGetRegionBySlug() {
        Region mienBac = catalogService.getRegionBySlug("mien-bac");
        assertNotNull(mienBac);
        assertEquals("Miền Bắc", mienBac.getName());
    }
}

