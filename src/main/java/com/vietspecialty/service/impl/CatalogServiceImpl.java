package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.Category;
import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Region;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.CategoryRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.RegionRepository;
import com.vietspecialty.service.CatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {

    private final RegionRepository regionRepository;
    private final ProvinceRepository provinceRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Cacheable(value = "regions", unless = "#result.isEmpty()")
    public List<Region> getAllRegions() {
        return regionRepository.findAllByOrderBySortOrderAsc();
    }

    @Override
    public Region getRegionBySlug(String slug) {
        return regionRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy vùng miền yêu cầu"));
    }

    @Override
    public List<Province> getProvincesByRegionId(Integer regionId) {
        if (regionId == null) {
            return provinceRepository.findAllByOrderByNameAsc();
        }
        return provinceRepository.findByRegionIdOrderByNameAsc(regionId);
    }

    @Override
    public List<Province> getAllProvinces() {
        return provinceRepository.findAllByOrderByNameAsc();
    }

    @Override
    @Cacheable(value = "categories", unless = "#result.isEmpty()")
    public List<Category> getAllActiveCategories() {
        return categoryRepository.findByIsActiveTrueOrderBySortOrderAsc();
    }

    @Override
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy danh mục yêu cầu"));
    }
}

