package com.vietspecialty.dto.request;

import java.math.BigDecimal;

public record ProductFilterRequest(
        String keyword,
        String regionSlug,
        Integer regionId,
        String provinceSlug,
        Integer provinceId,
        String categorySlug,
        Integer categoryId,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        Boolean minRating4,
        Boolean hasOcop,
        Long shopId,
        String sort,
        Integer page,
        Integer size
) {
    public ProductFilterRequest {
        if (page == null || page < 0) page = 0;
        if (size == null || size <= 0 || size > 100) size = 16;
        if (sort == null || sort.isBlank()) sort = "newest";
    }
}

