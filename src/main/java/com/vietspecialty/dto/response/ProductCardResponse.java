package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.ProductStatus;

import java.math.BigDecimal;

public record ProductCardResponse(
        Long id,
        String name,
        String slug,
        BigDecimal price,
        String formattedPrice,
        String unit,
        Integer stock,
        Integer ocopRating,
        Integer soldCount,
        BigDecimal avgRating,
        Integer reviewCount,
        String primaryImageUrl,
        String originDetail,
        String provinceName,
        String provinceSlug,
        String regionName,
        String regionSlug,
        String regionColorHex,
        Long shopId,
        String shopName,
        String shopSlug,
        ProductStatus status
) {
    public ProductCardResponse(
            Long id,
            String name,
            String slug,
            BigDecimal price,
            String formattedPrice,
            String unit,
            Integer stock,
            Integer ocopRating,
            Integer soldCount,
            BigDecimal avgRating,
            Integer reviewCount,
            String primaryImageUrl,
            String originDetail,
            String provinceName,
            String provinceSlug,
            String regionName,
            String regionSlug,
            String regionColorHex,
            Long shopId,
            String shopName,
            String shopSlug
    ) {
        this(id, name, slug, price, formattedPrice, unit, stock, ocopRating, soldCount, avgRating, reviewCount,
                primaryImageUrl, originDetail, provinceName, provinceSlug, regionName, regionSlug, regionColorHex,
                shopId, shopName, shopSlug, ProductStatus.ACTIVE);
    }

    public String priceFormatted() {
        return formattedPrice != null ? formattedPrice : (price != null ? price.toString() + "₫" : "0₫");
    }
}

