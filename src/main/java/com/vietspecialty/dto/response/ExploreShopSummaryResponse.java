package com.vietspecialty.dto.response;

public record ExploreShopSummaryResponse(
        Long id,
        String name,
        String slug,
        String logoUrl,
        String addressDetail,
        String provinceName,
        long activeProductCount
) {
}
