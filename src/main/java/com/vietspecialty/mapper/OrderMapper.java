package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.Order;
import com.vietspecialty.dto.response.OrderDetailResponse;
import com.vietspecialty.dto.response.OrderSummaryResponse;
import com.vietspecialty.util.MoneyUtils;

import java.util.Collections;
import java.util.List;

public final class OrderMapper {

    private OrderMapper() {
        // Utility class
    }

    public static OrderSummaryResponse toSummary(Order o) {
        if (o == null) return null;

        List<OrderSummaryResponse.OrderItemSummary> itemSummaries = o.getItems() == null
                ? Collections.emptyList()
                : o.getItems().stream()
                    .map(item -> new OrderSummaryResponse.OrderItemSummary(
                            item.getId(),
                            item.getProduct() != null ? item.getProduct().getId() : null,
                            item.getProductName(),
                            item.getProductImageUrl(),
                            item.getUnit(),
                            item.getUnitPrice(),
                            item.getQuantity(),
                            item.getLineTotal()
                    ))
                    .toList();

        return new OrderSummaryResponse(
                o.getId(),
                o.getCode(),
                o.getShop() != null ? o.getShop().getId() : null,
                o.getShop() != null ? o.getShop().getName() : null,
                o.getTotalAmount(),
                MoneyUtils.formatVnd(o.getTotalAmount()),
                o.getStatus(),
                o.getStatus().getDescription(),
                o.getPaymentMethod(),
                o.getPaymentStatus(),
                itemSummaries,
                o.getCreatedAt()
        );
    }

    public static OrderDetailResponse toDetail(Order o) {
        if (o == null) return null;

        List<OrderDetailResponse.OrderItemDetail> itemDetails = o.getItems() == null
                ? Collections.emptyList()
                : o.getItems().stream()
                    .map(item -> new OrderDetailResponse.OrderItemDetail(
                            item.getId(),
                            item.getProduct() != null ? item.getProduct().getId() : null,
                            item.getProductName(),
                            item.getProductImageUrl(),
                            item.getUnit(),
                            item.getUnitPrice(),
                            item.getQuantity(),
                            item.getLineTotal(),
                            false // hasReviewed được cập nhật ở service
                    ))
                    .toList();

        List<OrderDetailResponse.OrderStatusHistoryDetail> histories = o.getStatusHistories() == null
                ? Collections.emptyList()
                : o.getStatusHistories().stream()
                    .map(h -> new OrderDetailResponse.OrderStatusHistoryDetail(
                            h.getFromStatus(),
                            h.getToStatus(),
                            h.getToStatus().getDescription(),
                            h.getActorType(),
                            h.getNote(),
                            h.getCreatedAt()
                    ))
                    .toList();

        return new OrderDetailResponse(
                o.getId(),
                o.getCode(),
                o.getOrderGroup() != null ? o.getOrderGroup().getCode() : null,
                o.getShop() != null ? o.getShop().getId() : null,
                o.getShop() != null ? o.getShop().getName() : null,
                o.getReceiverName(),
                o.getReceiverPhone(),
                o.getShippingAddress(),
                o.getShippingMethod(),
                o.getItemsSubtotal(),
                o.getShippingFee(),
                o.getShopDiscount(),
                o.getPlatformDiscount(),
                o.getTotalAmount(),
                MoneyUtils.formatVnd(o.getTotalAmount()),
                o.getPaymentMethod(),
                o.getPaymentStatus(),
                o.getStatus(),
                o.getStatus().getDescription(),
                o.getCarrierName(),
                o.getTrackingCode(),
                o.getNote(),
                o.getCancelReason(),
                o.getCancelledBy(),
                o.getConfirmedAt(),
                o.getShippedAt(),
                o.getDeliveredAt(),
                o.getCancelledAt(),
                o.getCreatedAt(),
                itemDetails,
                histories
        );
    }
}

