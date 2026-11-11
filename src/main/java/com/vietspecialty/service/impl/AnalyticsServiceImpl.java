package com.vietspecialty.service.impl;

import com.vietspecialty.domain.entity.Shop;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.domain.enums.ProductStatus;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.RevenueStatResponse;
import com.vietspecialty.repository.OrderRepository;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.repository.ShopRepository;
import com.vietspecialty.service.AnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;

    @Override
    public DashboardSummaryResponse getSellerDashboardSummary(Long shopId) {
        BigDecimal totalRevenue = orderRepository.sumRevenueByShopAndStatus(shopId, OrderStatus.DELIVERED.name());
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        long totalOrders = orderRepository.countByShopId(shopId);
        long pendingOrders = orderRepository.countByShopIdAndStatus(shopId, OrderStatus.PENDING_CONFIRM);
        long shippingOrders = orderRepository.countByShopIdAndStatus(shopId, OrderStatus.SHIPPING);
        long completedOrders = orderRepository.countByShopIdAndStatus(shopId, OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByShopIdAndStatus(shopId, OrderStatus.CANCELLED);

        long totalProducts = productRepository.countByShopId(shopId);
        long lowStockProducts = productRepository.findLowStockProductsByShopId(shopId, PageRequest.of(0, 100)).size();

        return new DashboardSummaryResponse(
                totalRevenue,
                totalOrders,
                pendingOrders,
                shippingOrders,
                completedOrders,
                cancelledOrders,
                totalProducts,
                lowStockProducts,
                5.0
        );
    }

    @Override
    public List<RevenueStatResponse> getSellerDailyRevenue(Long shopId, int days) {
        LocalDateTime startDate = LocalDate.now().minusDays(days - 1).atStartOfDay();
        List<Object[]> rawStats = orderRepository.getDailyRevenueByShop(shopId, startDate);

        return mapToDailyRevenueList(rawStats, days);
    }

    @Override
    public DashboardSummaryResponse getAdminDashboardSummary() {
        BigDecimal totalRevenue = orderRepository.sumTotalRevenuePlatform(OrderStatus.DELIVERED.name());
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        long totalOrders = orderRepository.count();
        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING_CONFIRM);
        long shippingOrders = orderRepository.countByStatus(OrderStatus.SHIPPING);
        long completedOrders = orderRepository.countByStatus(OrderStatus.DELIVERED);
        long cancelledOrders = orderRepository.countByStatus(OrderStatus.CANCELLED);

        long totalProducts = productRepository.count();
        long lowStockProducts = 0;

        return new DashboardSummaryResponse(
                totalRevenue,
                totalOrders,
                pendingOrders,
                shippingOrders,
                completedOrders,
                cancelledOrders,
                totalProducts,
                lowStockProducts,
                5.0
        );
    }

    @Override
    public List<RevenueStatResponse> getAdminDailyRevenue(int days) {
        LocalDateTime startDate = LocalDate.now().minusDays(days - 1).atStartOfDay();
        List<Object[]> rawStats = orderRepository.getDailyRevenuePlatform(startDate);

        return mapToDailyRevenueList(rawStats, days);
    }

    private List<RevenueStatResponse> mapToDailyRevenueList(List<Object[]> rawStats, int days) {
        Map<String, RevenueStatResponse> statMap = new HashMap<>();
        if (rawStats != null) {
            for (Object[] row : rawStats) {
                String label = (String) row[0];
                BigDecimal rev = row[1] instanceof BigDecimal ? (BigDecimal) row[1] : new BigDecimal(row[1].toString());
                Long cnt = row[2] instanceof Number ? ((Number) row[2]).longValue() : 0L;
                statMap.put(label, new RevenueStatResponse(label, rev, cnt));
            }
        }

        List<RevenueStatResponse> result = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        LocalDate today = LocalDate.now();

        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = today.minusDays(i);
            String label = date.format(fmt);
            RevenueStatResponse stat = statMap.getOrDefault(label, new RevenueStatResponse(label, BigDecimal.ZERO, 0L));
            result.add(stat);
        }

        return result;
    }
}
