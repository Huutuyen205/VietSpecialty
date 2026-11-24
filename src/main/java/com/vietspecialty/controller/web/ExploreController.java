package com.vietspecialty.controller.web;

import com.vietspecialty.dto.response.ExploreProvinceDetailResponse;
import com.vietspecialty.dto.response.ExploreProvinceSummaryResponse;
import com.vietspecialty.dto.response.ExploreRegionSummaryResponse;
import com.vietspecialty.dto.response.ExploreRegionsResponse;
import com.vietspecialty.service.ExploreService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

@Controller
@RequiredArgsConstructor
public class ExploreController {

    private static final int DEFAULT_TOP_LIMIT = 6;

    private final ExploreService exploreService;

    @GetMapping("/explore")
    public String explorePage(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String province,
            Model model) {
        ExploreRegionsResponse overview = exploreService.getExploreRegionsOverview();

        String normalizedRegion = region != null ? region.trim() : "";
        String normalizedProvince = province != null ? province.trim() : "";

        Optional<ExploreRegionSummaryResponse> matchedRegion = overview.regions().stream()
                .filter(r -> r.slug().equals(normalizedRegion))
                .findFirst();

        Optional<ExploreProvinceSummaryResponse> matchedProvince = overview.regions().stream()
                .flatMap(r -> r.provinces().stream())
                .filter(p -> p.slug().equals(normalizedProvince))
                .findFirst();

        ExploreProvinceDetailResponse selectedProvinceDetail = null;
        String selectedRegionSlug = matchedRegion.map(ExploreRegionSummaryResponse::slug).orElse("");

        if (matchedProvince.isPresent()) {
            ExploreProvinceSummaryResponse prov = matchedProvince.get();
            selectedProvinceDetail = exploreService.getProvinceDetail(prov.slug(), DEFAULT_TOP_LIMIT);
            selectedRegionSlug = prov.regionSlug();
        } else {
            Optional<ExploreProvinceSummaryResponse> fallbackProvince = matchedRegion
                    .map(r -> r.provinces().stream()
                            .filter(p -> p.activeProductCount() > 0)
                            .findFirst()
                            .or(() -> r.provinces().stream().findFirst()))
                    .orElseGet(() -> overview.regions().stream()
                            .flatMap(r -> r.provinces().stream())
                            .filter(p -> p.activeProductCount() > 0)
                            .findFirst()
                            .or(() -> overview.regions().stream()
                                    .flatMap(r -> r.provinces().stream())
                                    .findFirst()));

            if (fallbackProvince.isPresent()) {
                selectedProvinceDetail = exploreService.getProvinceDetail(
                        fallbackProvince.get().slug(),
                        DEFAULT_TOP_LIMIT
                );
            }
        }

        model.addAttribute("pageTitle", "Bản đồ đặc sản 4 miền & 34 tỉnh thành");
        model.addAttribute("overview", overview);
        model.addAttribute("selectedRegionSlug", selectedRegionSlug);
        model.addAttribute("selectedProvinceDetail", selectedProvinceDetail);

        return "web/explore";
    }
}
