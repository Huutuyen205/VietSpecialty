package com.vietspecialty.service;

import com.vietspecialty.dto.response.ExploreProvinceDetailResponse;
import com.vietspecialty.dto.response.ExploreRegionsResponse;

public interface ExploreService {

    ExploreRegionsResponse getExploreRegionsOverview();

    ExploreProvinceDetailResponse getProvinceDetail(String slug, int topLimit);
}
