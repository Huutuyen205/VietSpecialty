package com.vietspecialty.dto.response;

import java.util.List;

public record ExploreProvinceDetailResponse(
        Integer id,
        String name,
        String slug,
        Integer regionId,
        String regionName,
        String regionSlug,
        String regionColorHex,
        long activeProductCount,
        long ocopProductCount,
        long approvedShopCount,
        String productsFilterUrl,
        List<ExploreShopSummaryResponse> approvedShops,
        List<ProductCardResponse> topSpecialties
) {
}
