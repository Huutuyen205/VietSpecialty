package com.vietspecialty.dto.response;

import java.util.List;

public record ExploreRegionsResponse(
        long totalRegions,
        long totalProvinces,
        long totalActiveProducts,
        long totalOcopProducts,
        long totalApprovedShops,
        List<ExploreRegionSummaryResponse> regions
) {
}
