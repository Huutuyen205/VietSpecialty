package com.vietspecialty.controller.api;

import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.response.ExploreProvinceDetailResponse;
import com.vietspecialty.dto.response.ExploreRegionsResponse;
import com.vietspecialty.service.ExploreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/explore")
@RequiredArgsConstructor
public class ExploreApiController {

    private final ExploreService exploreService;

    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<ExploreRegionsResponse>> getRegionsOverview() {
        return ResponseEntity.ok(ApiResponse.success(exploreService.getExploreRegionsOverview()));
    }

    @GetMapping("/provinces/{slug}")
    public ResponseEntity<ApiResponse<ExploreProvinceDetailResponse>> getProvinceDetail(
            @PathVariable String slug,
            @RequestParam(defaultValue = "6") int limit) {
        return ResponseEntity.ok(ApiResponse.success(exploreService.getProvinceDetail(slug, limit)));
    }
}
