package com.vietspecialty.dto.response;

import java.math.BigDecimal;

public record DashboardSummaryResponse(
        BigDecimal totalRevenue,
        Long totalOrders,
        Long pendingOrders,
        Long shippingOrders,
        Long completedOrders,
        Long cancelledOrders,
        Long totalProducts,
        Long lowStockProducts,
        Double avgRating
) {}

