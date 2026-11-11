package com.vietspecialty.service;

import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.RevenueStatResponse;

import java.util.List;

public interface AnalyticsService {

    DashboardSummaryResponse getSellerDashboardSummary(Long shopId);

    List<RevenueStatResponse> getSellerDailyRevenue(Long shopId, int days);

    DashboardSummaryResponse getAdminDashboardSummary();

    List<RevenueStatResponse> getAdminDailyRevenue(int days);
}

