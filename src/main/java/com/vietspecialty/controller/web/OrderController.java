package com.vietspecialty.controller.web;

import com.vietspecialty.domain.entity.Order;
import com.vietspecialty.domain.entity.OrderItem;
import com.vietspecialty.domain.enums.OrderPaymentStatus;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.dto.ApiResponse;
import com.vietspecialty.dto.request.CartItemRequest;
import com.vietspecialty.dto.response.OrderDetailResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;
import com.vietspecialty.exception.BusinessException;
import com.vietspecialty.exception.ErrorCode;
import com.vietspecialty.repository.OrderRepository;
import com.vietspecialty.security.CurrentUser;
import com.vietspecialty.security.CustomUserDetails;
import com.vietspecialty.service.CartService;
import com.vietspecialty.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final OrderRepository orderRepository;

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

    @PostMapping("/{id}/reorder")
    @Transactional
    public Object reorder(
            @PathVariable Long id,
            @CurrentUser CustomUserDetails userDetails,
            @RequestHeader(value = "Accept", required = false) String acceptHeader
    ) {
        boolean isJson = acceptHeader != null && acceptHeader.contains("application/json");
        if (userDetails == null) {
            if (isJson) {
                return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
            }
            return "redirect:/login?redirect=/orders";
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userDetails.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác trên đơn hàng này");
        }

        int addedCount = 0;
        if (order.getItems() != null) {
            for (OrderItem item : order.getItems()) {
                if (item.getProduct() != null && item.getProduct().getStock() > 0) {
                    int qty = Math.min(item.getQuantity(), item.getProduct().getStock());
                    try {
                        cartService.addToCart(userDetails.getId(), new CartItemRequest(item.getProduct().getId(), qty));
                        addedCount++;
                    } catch (Exception ignored) {}
                }
            }
        }

        if (isJson) {
            return ResponseEntity.ok(ApiResponse.success("Đã thêm " + addedCount + " đặc sản vào giỏ hàng", null));
        }

        return "redirect:/cart";
    }

    @PostMapping("/{id}/refund-request")
    @Transactional
    public Object requestRefund(
            @PathVariable Long id,
            @CurrentUser CustomUserDetails userDetails,
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            RedirectAttributes redirectAttributes
    ) {
        boolean isJson = acceptHeader != null && acceptHeader.contains("application/json");
        if (userDetails == null) {
            if (isJson) {
                return ResponseEntity.status(401).body(ApiResponse.error("Vui lòng đăng nhập"));
            }
            return "redirect:/login";
        }

        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getUser().getId().equals(userDetails.getId())) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, "Bạn không có quyền thao tác trên đơn hàng này");
        }

        order.setPaymentStatus(OrderPaymentStatus.REFUND_PENDING);
        orderRepository.save(order);

        if (isJson) {
            return ResponseEntity.ok(ApiResponse.success("Đã gửi yêu cầu hoàn tiền cho Ban Quản Trị", null));
        }

        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi yêu cầu hoàn tiền cho Ban Quản Trị sàn.");
        return "redirect:/orders/" + order.getCode();
    }
}
