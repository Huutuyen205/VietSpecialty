package com.vietspecialty.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderGroupSummaryResponse(
        Long id,
        String groupCode,
        BigDecimal grandTotal,
        String grandTotalFormatted,
        String paymentMethod,
        String paymentStatus,
        List<OrderSummaryItem> orders,
        LocalDateTime createdAt
) {
    public record OrderSummaryItem(
            Long id,
            String orderCode,
            String shopName,
            BigDecimal totalAmount,
            String totalAmountFormatted,
            String status
    ) {}
}

