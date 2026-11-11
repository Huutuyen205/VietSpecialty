package com.vietspecialty.controller.web;

import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.dto.response.OrderDetailResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Slf4j
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public String myOrders(
            @CurrentUser CustomUserDetails userDetails,
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/orders";
        }

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<OrderSummaryResponse> orderPage = orderService.getBuyerOrders(userDetails.getId(), status, pageable);

        model.addAttribute("orderPage", orderPage);
        model.addAttribute("selectedStatus", status != null ? status.name() : "ALL");
        model.addAttribute("statuses", OrderStatus.values());

        return "web/orders/index";
    }

    @GetMapping("/{code}")
    public String orderDetail(
            @CurrentUser CustomUserDetails userDetails,
            @PathVariable String code,
            Model model
    ) {
        if (userDetails == null) {
            return "redirect:/login?redirect=/orders/" + code;
        }

        OrderDetailResponse detail = orderService.getOrderDetail(code, userDetails.getId());
        model.addAttribute("order", detail);

        return "web/orders/detail";
    }
}

