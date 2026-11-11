package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.ProductStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ProductDetailResponse(
        Long id,
        String name,
        String slug,
        String shortDescription,
        String description,
        BigDecimal price,
        String formattedPrice,
        Integer stock,
        String unit,
        Integer weightGram,
        String originDetail,
        String shelfLife,
        String storageInstructions,
        Integer ocopRating,
        Integer soldCount,
        BigDecimal avgRating,
        Integer reviewCount,
        ProductStatus status,
        List<ProductImageResponse> images,
        ProvinceResponse province,
        RegionResponse region,
        CategoryResponse category,
        ShopResponse shop,
        LocalDateTime createdAt
) {
    public record ProductImageResponse(Long id, String imageUrl, Boolean isPrimary, Integer sortOrder) {}
    public record ProvinceResponse(Integer id, String name, String slug) {}
    public record RegionResponse(Integer id, String name, String slug, String colorHex) {}
    public record CategoryResponse(Integer id, String name, String slug) {}
}

