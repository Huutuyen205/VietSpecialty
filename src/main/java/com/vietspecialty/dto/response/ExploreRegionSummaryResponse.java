package com.vietspecialty.dto.response;

import java.util.List;

public record ExploreRegionSummaryResponse(
        Integer id,
        String name,
        String slug,
        String description,
        String colorHex,
        Integer sortOrder,
        long provinceCount,
        long activeProductCount,
        long ocopProductCount,
        long approvedShopCount,
        List<ExploreProvinceSummaryResponse> provinces
) {
}
