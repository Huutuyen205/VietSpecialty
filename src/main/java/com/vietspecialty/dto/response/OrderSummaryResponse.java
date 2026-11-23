package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.OrderPaymentStatus;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.domain.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderSummaryResponse(
        Long id,
        String code,
        Long shopId,
        String shopName,
        String receiverName,
        String receiverPhone,
        BigDecimal totalAmount,
        String formattedTotalAmount,
        OrderStatus status,
        String statusDescription,
        PaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus,
        List<OrderItemSummary> items,
        LocalDateTime createdAt
) {
    public OrderSummaryResponse(
            Long id,
            String code,
            Long shopId,
            String shopName,
            BigDecimal totalAmount,
            String formattedTotalAmount,
            OrderStatus status,
            String statusDescription,
            PaymentMethod paymentMethod,
            OrderPaymentStatus paymentStatus,
            List<OrderItemSummary> items,
            LocalDateTime createdAt
    ) {
        this(id, code, shopId, shopName, null, null, totalAmount, formattedTotalAmount, status, statusDescription, paymentMethod, paymentStatus, items, createdAt);
    }
    public record OrderItemSummary(
            Long id,
            Long productId,
            String productName,
            String productImageUrl,
            String unit,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineTotal
    ) {}
}

