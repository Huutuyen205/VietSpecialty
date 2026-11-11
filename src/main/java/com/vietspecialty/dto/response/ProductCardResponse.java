package com.vietspecialty.dto.response;

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
        String shopSlug
) {
    public String priceFormatted() {
        return formattedPrice != null ? formattedPrice : (price != null ? price.toString() + "₫" : "0₫");
    }
}

