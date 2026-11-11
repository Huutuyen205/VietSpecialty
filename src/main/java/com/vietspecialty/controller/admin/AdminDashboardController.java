package com.vietspecialty.controller.admin;

import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.RevenueStatResponse;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AnalyticsService analyticsService;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        DashboardSummaryResponse stats = analyticsService.getAdminDashboardSummary();
        long totalShops = shopRepository.count();
        long totalUsers = userRepository.count();

        model.addAttribute("stats", stats);
        model.addAttribute("totalShops", totalShops);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("activeNav", "dashboard");

        return "admin/dashboard";
    }

    @GetMapping("/api/admin/analytics/revenue")
    @ResponseBody
    public ApiResponse<List<RevenueStatResponse>> getAdminDailyRevenue(
            @RequestParam(name = "days", defaultValue = "7") int days
    ) {
        List<RevenueStatResponse> stats = analyticsService.getAdminDailyRevenue(days);
        return ApiResponse.success(stats);
    }
}

