package com.vietspecialty.controller.web;

import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.dto.response.OrderDetailResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;
import com.vietspecialty.dto.response.ShopResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.OrderService;
import com.vietspecialty.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/seller/orders")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SELLER')")
public class SellerOrderController {

    private final OrderService orderService;
    private final ShopService shopService;

    @GetMapping
    public String listOrders(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<OrderSummaryResponse> orderPage = orderService.getSellerOrders(shop.id(), status, search, pageable);

        model.addAttribute("shop", shop);
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("selectedStatus", status != null ? status.name() : "ALL");
        model.addAttribute("search", search);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("activeNav", "orders");

        return "seller/orders/index";
    }

    @GetMapping("/{code}")
    public String orderDetail(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable String code,
            Model model
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        OrderDetailResponse detail = orderService.getSellerOrderDetail(code, shop.id());

        model.addAttribute("shop", shop);
        model.addAttribute("order", detail);
        model.addAttribute("activeNav", "orders");

        return "seller/orders/detail";
    }

    @PostMapping("/{id}/confirm")
    public String confirmOrder(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        try {
            orderService.confirmSellerOrder(id, shop.id());
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận đơn hàng và chuyển sang đóng gói chuẩn bị hàng.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/orders";
    }

    @PostMapping("/{id}/ship")
    public String shipOrder(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam String carrierName,
            @RequestParam String trackingCode,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        try {
            orderService.shipSellerOrder(id, shop.id(), carrierName, trackingCode);
            redirectAttributes.addFlashAttribute("successMessage", "Đã bàn giao đơn hàng cho đơn vị vận chuyển.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/orders";
    }

    @PostMapping("/{id}/cancel")
    public String cancelOrder(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Gian hàng hết sản phẩm hoặc không thể giao") String reason,
            RedirectAttributes redirectAttributes
    ) {
        ShopResponse shop = shopService.getShopByOwnerId(userDetails.getId());
        try {
            orderService.cancelSellerOrder(id, shop.id(), reason);
            redirectAttributes.addFlashAttribute("successMessage", "Đã hủy đơn hàng thành công.");
        } catch (BusinessException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/seller/orders";
    }
}

