package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.entity.Province;
import com.vietspecialty.domain.entity.Region;
import com.vietspecialty.domain.enums.OrderPaymentStatus;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.RevenueStatResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.repository.OrderRepository;
import com.vietspecialty.repository.ProvinceRepository;
import com.vietspecialty.repository.RegionRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.repository.UserRepository;
import com.vietspecialty.service.AnalyticsService;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardController {

    private final AnalyticsService analyticsService;
    private final ShopRepository shopRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final RegionRepository regionRepository;
    private final ProvinceRepository provinceRepository;
    private final ShopService shopService;

    @GetMapping("/admin/dashboard")
    public String dashboard(Model model) {
        DashboardSummaryResponse stats = analyticsService.getAdminDashboardSummary();
        long totalShops = shopRepository.count();
        long totalUsers = userRepository.count();
        long pendingShopsCount = shopRepository.countByStatus(ShopStatus.PENDING);
        long pendingRefundsCount = orderRepository.countByPaymentStatus(OrderPaymentStatus.REFUND_PENDING);
        List<ShopResponse> pendingShops = shopService.getShopsByStatus(ShopStatus.PENDING, PageRequest.of(0, 5)).getContent();
        List<RevenueStatResponse> regionStats = buildRegionRevenueStats();

        model.addAttribute("stats", stats);
        model.addAttribute("totalShops", totalShops);
        model.addAttribute("totalUsers", totalUsers);
        model.addAttribute("pendingShopsCount", pendingShopsCount);
        model.addAttribute("pendingRefundsCount", pendingRefundsCount);
        model.addAttribute("pendingShops", pendingShops);
        model.addAttribute("regionStats", regionStats);
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

    @GetMapping("/api/admin/analytics/regions")
    @ResponseBody
    public ApiResponse<List<RevenueStatResponse>> getAdminRegionRevenue() {
        return ApiResponse.success(buildRegionRevenueStats());
    }

    private List<RevenueStatResponse> buildRegionRevenueStats() {
        List<Region> regions = regionRepository.findAllByOrderBySortOrderAsc();
        Map<Integer, BigDecimal> revByRegion = new LinkedHashMap<>();
        Map<Integer, Long> countByRegion = new LinkedHashMap<>();
        for (Region r : regions) {
            revByRegion.put(r.getId(), BigDecimal.ZERO);
            countByRegion.put(r.getId(), 0L);
        }

        for (Object[] row : orderRepository.sumRevenueByRegion(OrderStatus.DELIVERED)) {
            Integer regId = ((Number) row[0]).intValue();
            BigDecimal amount = row[1] instanceof BigDecimal b ? b : new BigDecimal(row[1].toString());
            revByRegion.merge(regId, amount, BigDecimal::add);
            countByRegion.merge(regId, ((Number) row[2]).longValue(), Long::sum);
        }

        List<RevenueStatResponse> result = new ArrayList<>();
        for (Region r : regions) {
            long provinceCount = provinceRepository.findByRegionIdOrderByNameAsc(r.getId()).size();
            long orderCount = countByRegion.getOrDefault(r.getId(), 0L);
            result.add(new RevenueStatResponse(
                    r.getName(),
                    revByRegion.getOrDefault(r.getId(), BigDecimal.ZERO),
                    orderCount > 0 ? orderCount : provinceCount
            ));
        }
        return result;
    }
}


