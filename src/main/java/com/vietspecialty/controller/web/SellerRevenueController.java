package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Order;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.domain.enums.ShopStatus;
import com.vietspecialty.dto.response.DashboardSummaryResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.OrderRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.AnalyticsService;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Controller
@RequestMapping("/seller/revenue")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerRevenueController {

    private final ShopService shopService;
    private final AnalyticsService analyticsService;
    private final OrderService orderService;
    private final OrderRepository orderRepository;

    @GetMapping
    public String revenuePage(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        if (shop.status() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_APPROVED);
        }

        DashboardSummaryResponse stats = analyticsService.getSellerDashboardSummary(shop.id());
        Page<OrderSummaryResponse> deliveredOrders = orderService.getSellerOrders(
                shop.id(), OrderStatus.DELIVERED, null, PageRequest.of(Math.max(0, page), Math.max(1, size))
        );

        BigDecimal avgOrderValue = BigDecimal.ZERO;
        if (stats.completedOrders() > 0 && stats.totalRevenue() != null) {
            avgOrderValue = stats.totalRevenue().divide(
                    BigDecimal.valueOf(stats.completedOrders()), 0, RoundingMode.HALF_UP
            );
        }

        model.addAttribute("shop", shop);
        model.addAttribute("stats", stats);
        model.addAttribute("avgOrderValue", avgOrderValue);
        model.addAttribute("deliveredOrders", deliveredOrders);
        model.addAttribute("activeNav", "revenue");

        return "seller/revenue/index";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportRevenueCsv(@CurrentUser CustomUserDetails userDetails) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        if (shop.status() != ShopStatus.APPROVED) {
            throw new BusinessException(ErrorCode.SHOP_NOT_APPROVED);
        }

        List<Order> deliveredOrders = orderRepository.findByShopIdAndStatusOrderByCreatedAtDesc(
                shop.id(), OrderStatus.DELIVERED
        );

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder csv = new StringBuilder();
        // UTF-8 BOM để mở trực tiếp trên Excel không bị lỗi font tiếng Việt
        csv.append('\uFEFF');
        csv.append("Mã đơn hàng,Ngày đặt,Ngày giao,Người nhận,Số điện thoại,Tiền hàng (VNĐ),Phí vận chuyển (VNĐ),Giảm giá Shop (VNĐ),Trợ giá Sàn (VNĐ),Thực thu (VNĐ),Phương thức TT\r\n");

        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (Order o : deliveredOrders) {
            String createdStr = o.getCreatedAt() != null ? o.getCreatedAt().format(fmt) : "";
            String deliveredStr = o.getDeliveredAt() != null ? o.getDeliveredAt().format(fmt) : createdStr;
            BigDecimal amount = o.getTotalAmount() != null ? o.getTotalAmount() : BigDecimal.ZERO;
            totalRevenue = totalRevenue.add(amount);

            csv.append(escapeCsv(o.getCode())).append(',')
                    .append(escapeCsv(createdStr)).append(',')
                    .append(escapeCsv(deliveredStr)).append(',')
                    .append(escapeCsv(o.getReceiverName())).append(',')
                    .append(escapeCsv(o.getReceiverPhone())).append(',')
                    .append(o.getItemsSubtotal() != null ? o.getItemsSubtotal() : BigDecimal.ZERO).append(',')
                    .append(o.getShippingFee() != null ? o.getShippingFee() : BigDecimal.ZERO).append(',')
                    .append(o.getShopDiscount() != null ? o.getShopDiscount() : BigDecimal.ZERO).append(',')
                    .append(o.getPlatformDiscount() != null ? o.getPlatformDiscount() : BigDecimal.ZERO).append(',')
                    .append(amount).append(',')
                    .append(o.getPaymentMethod() != null ? o.getPaymentMethod().name() : "COD")
                    .append("\r\n");
        }

        csv.append("TỔNG CỘNG,,,,,,,,,").append(totalRevenue).append(",\r\n");

        byte[] bytes = csv.toString().getBytes(StandardCharsets.UTF_8);
        String filename = "doanh-thu-" + (shop.slug() != null ? shop.slug() : "shop") + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(bytes);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        String cleaned = val.replace("\"", "\"\"");
        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n")) {
            return "\"" + cleaned + "\"";
        }
        return cleaned;
    }
}
