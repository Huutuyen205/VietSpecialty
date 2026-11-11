package com.vietspecialty;

import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.RegionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class VietSpecialtyApplicationTests {

    @Autowired
    private RegionRepository regionRepository;

    @Autowired
    private ProvinceRepository provinceRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("Khởi động Spring Boot, Flyway nạp schema 21 bảng & Hibernate validate thành công")
    void contextLoadsAndMasterDataPresent() {
        assertEquals(4, regionRepository.count(), "Phải có đủ 4 vùng miền");
        assertEquals(34, provinceRepository.count(), "Phải có đủ 34 tỉnh thành sau sắp xếp 2025");
        assertEquals(8, categoryRepository.count(), "Phải có đủ 8 danh mục đặc sản");
    }
}

