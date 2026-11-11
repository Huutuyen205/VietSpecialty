package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.ActorType;
import com.vietspecialty.domain.enums.OrderPaymentStatus;
import com.vietspecialty.domain.enums.OrderStatus;
import com.vietspecialty.domain.enums.PaymentMethod;
import com.vietspecialty.domain.enums.ShippingMethod;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderDetailResponse(
        Long id,
        String code,
        String groupCode,
        Long shopId,
        String shopName,
        String receiverName,
        String receiverPhone,
        String shippingAddress,
        ShippingMethod shippingMethod,
        BigDecimal itemsSubtotal,
        BigDecimal shippingFee,
        BigDecimal shopDiscount,
        BigDecimal platformDiscount,
        BigDecimal totalAmount,
        String formattedTotalAmount,
        PaymentMethod paymentMethod,
        OrderPaymentStatus paymentStatus,
        OrderStatus status,
        String statusDescription,
        String carrierName,
        String trackingCode,
        String note,
        String cancelReason,
        ActorType cancelledBy,
        LocalDateTime confirmedAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt,
        LocalDateTime createdAt,
        List<OrderItemDetail> items,
        List<OrderStatusHistoryDetail> histories
) {
    public record OrderItemDetail(
            Long id,
            Long productId,
            String productName,
            String productImageUrl,
            String unit,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineTotal,
            Boolean hasReviewed
    ) {}

    public record OrderStatusHistoryDetail(
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String toStatusDescription,
            ActorType actorType,
            String note,
            LocalDateTime createdAt
    ) {}
}

