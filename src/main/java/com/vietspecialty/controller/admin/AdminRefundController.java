package com.vietspecialty.controller.admin;

import com.vietspecialty.domain.entity.Order;
import com.vietspecialty.domain.enums.OrderPaymentStatus;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.repository.OrderRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.OrderService;
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

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/admin/refunds")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminRefundController {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    @GetMapping
    public String listRefunds(
            @RequestParam(value = "status", required = false) OrderPaymentStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "15") int size,
            Model model
    ) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Order> refundOrders;
        if (status != null) {
            refundOrders = orderRepository.findByPaymentStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            refundOrders = orderRepository.findByPaymentStatusInOrderByCreatedAtDesc(
                    List.of(OrderPaymentStatus.REFUND_PENDING, OrderPaymentStatus.REFUNDED),
                    pageable
            );
        }

        long pendingCount = orderRepository.countByPaymentStatus(OrderPaymentStatus.REFUND_PENDING);
        long refundedCount = orderRepository.countByPaymentStatus(OrderPaymentStatus.REFUNDED);

        model.addAttribute("orders", refundOrders);
        model.addAttribute("currentStatus", status);
        model.addAttribute("pendingCount", pendingCount);
        model.addAttribute("refundedCount", refundedCount);
        model.addAttribute("activeNav", "refunds");

        return "admin/refunds/index";
    }

    @PostMapping({"/{id}/confirm", "/{id}/approve", "/{id}/refund"})
    public String confirmRefund(
            @PathVariable("id") Long orderId,
            @CurrentUser CustomUserDetails adminDetails,
            RedirectAttributes redirectAttributes
    ) {
        try {
            orderService.confirmRefund(orderId, adminDetails.getId());
            redirectAttributes.addFlashAttribute("successMessage", "Đã xác nhận hoàn tiền đơn hàng thành công (REFUND_PENDING -> REFUNDED)!");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/refunds";
    }
}
