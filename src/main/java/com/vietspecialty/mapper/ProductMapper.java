package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.entity.ProductImage;
import com.vietspecialty.dto.response.ProductCardResponse;
import com.vietspecialty.dto.response.ProductDetailResponse;
import com.vietspecialty.util.MoneyUtils;

import java.util.Collections;
import java.util.List;

public final class ProductMapper {

    private ProductMapper() {
        // Utility class
    }

    public static ProductCardResponse toCardResponse(Product p) {
        if (p == null) return null;

        String primaryImage = null;
        if (p.getImages() != null && !p.getImages().isEmpty()) {
            primaryImage = p.getImages().stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                    .findFirst()
                    .map(ProductImage::getImageUrl)
                    .orElse(p.getImages().get(0).getImageUrl());
        }

        String provinceName = p.getProvince() != null ? p.getProvince().getName() : null;
        String provinceSlug = p.getProvince() != null ? p.getProvince().getSlug() : null;
        String regionName = (p.getProvince() != null && p.getProvince().getRegion() != null)
                ? p.getProvince().getRegion().getName() : null;
        String regionSlug = (p.getProvince() != null && p.getProvince().getRegion() != null)
                ? p.getProvince().getRegion().getSlug() : null;
        String regionColorHex = (p.getProvince() != null && p.getProvince().getRegion() != null)
                ? p.getProvince().getRegion().getColorHex() : null;

        Long shopId = p.getShop() != null ? p.getShop().getId() : null;
        String shopName = p.getShop() != null ? p.getShop().getName() : null;
        String shopSlug = p.getShop() != null ? p.getShop().getSlug() : null;

        return new ProductCardResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getPrice(),
                MoneyUtils.formatVnd(p.getPrice()),
                p.getUnit(),
                p.getStock(),
                p.getOcopRating(),
                p.getSoldCount(),
                p.getAvgRating(),
                p.getReviewCount(),
                primaryImage,
                p.getOriginDetail(),
                provinceName,
                provinceSlug,
                regionName,
                regionSlug,
                regionColorHex,
                shopId,
                shopName,
                shopSlug,
                p.getStatus()
        );
    }

    public static ProductDetailResponse toDetailResponse(Product p) {
        if (p == null) return null;

        List<ProductDetailResponse.ProductImageResponse> imageResponses = p.getImages() == null
                ? Collections.emptyList()
                : p.getImages().stream()
                    .map(img -> new ProductDetailResponse.ProductImageResponse(
                            img.getId(), img.getImageUrl(), img.getIsPrimary(), img.getSortOrder()))
                    .toList();

        ProductDetailResponse.ProvinceResponse provinceRes = p.getProvince() != null
                ? new ProductDetailResponse.ProvinceResponse(p.getProvince().getId(), p.getProvince().getName(), p.getProvince().getSlug())
                : null;

        ProductDetailResponse.RegionResponse regionRes = (p.getProvince() != null && p.getProvince().getRegion() != null)
                ? new ProductDetailResponse.RegionResponse(
                        p.getProvince().getRegion().getId(),
                        p.getProvince().getRegion().getName(),
                        p.getProvince().getRegion().getSlug(),
                        p.getProvince().getRegion().getColorHex())
                : null;

        ProductDetailResponse.CategoryResponse categoryRes = p.getCategory() != null
                ? new ProductDetailResponse.CategoryResponse(p.getCategory().getId(), p.getCategory().getName(), p.getCategory().getSlug())
                : null;

        return new ProductDetailResponse(
                p.getId(),
                p.getName(),
                p.getSlug(),
                p.getShortDescription(),
                p.getDescription(),
                p.getPrice(),
                MoneyUtils.formatVnd(p.getPrice()),
                p.getStock(),
                p.getUnit(),
                p.getWeightGram(),
                p.getOriginDetail(),
                p.getShelfLife(),
                p.getStorageInstructions(),
                p.getOcopRating(),
                p.getSoldCount(),
                p.getAvgRating(),
                p.getReviewCount(),
                p.getStatus(),
                imageResponses,
                provinceRes,
                regionRes,
                categoryRes,
                ShopMapper.toResponse(p.getShop()),
                p.getCreatedAt()
        );
    }
}

