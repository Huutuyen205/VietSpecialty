package com.vietspecialty.controller.api;

import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.response.RevenueStatResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.AnalyticsService;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/seller/analytics")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerAnalyticsApiController {

    private final AnalyticsService analyticsService;
    private final ShopService shopService;

    @GetMapping("/revenue")
    public ApiResponse<List<RevenueStatResponse>> getDailyRevenue(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(name = "days", defaultValue = "7") int days
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        List<RevenueStatResponse> stats = analyticsService.getSellerDailyRevenue(shop.id(), days);
        return ApiResponse.success(stats);
    }
}

