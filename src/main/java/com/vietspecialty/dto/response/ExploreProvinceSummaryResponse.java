package com.vietspecialty.dto.response;

public record ExploreProvinceSummaryResponse(
        Integer id,
        String name,
        String slug,
        Integer regionId,
        String regionName,
        String regionSlug,
        String regionColorHex,
        long activeProductCount,
        long ocopProductCount,
        long approvedShopCount
) {
}
