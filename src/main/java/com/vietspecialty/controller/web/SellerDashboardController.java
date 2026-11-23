package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Product;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.ProductRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.AnalyticsService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Slf4j
@Controller
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerDashboardController {

    private final ShopService shopService;
    private final ProductRepository productRepository;
    private final AnalyticsService analyticsService;
    private final OrderService orderService;

    @GetMapping("/seller/dashboard")
    public String dashboard(@CurrentUser CustomUserDetails userDetails, Model model) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        if (shop.status() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_APPROVED);
        }

        DashboardSummaryResponse stats = analyticsService.getSellerDashboardSummary(shop.id());
        List<Product> lowStockProducts = productRepository.findLowStockProductsByShopId(shop.id(), PageRequest.of(0, 5));
        List<Product> topSellingProducts = productRepository.findTopSellingByShopId(shop.id(), PageRequest.of(0, 5));
        List<OrderSummaryResponse> recentOrders = orderService.getSellerOrders(shop.id(), null, null, PageRequest.of(0, 5)).getContent();

        model.addAttribute("shop", shop);
        model.addAttribute("stats", stats);
        model.addAttribute("lowStockProducts", lowStockProducts);
        model.addAttribute("topSellingProducts", topSellingProducts);
        model.addAttribute("recentOrders", recentOrders);
        model.addAttribute("activeNav", "dashboard");

        return "seller/dashboard";
    }
}
