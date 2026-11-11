package com.vietspecialty.dto.response;

import com.vietspecialty.domain.enums.DiscountType;
import com.vietspecialty.domain.enums.VoucherScope;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record VoucherResponse(
        Long id,
        String code,
        String name,
        VoucherScope scope,
        Long shopId,
        String shopName,
        DiscountType discountType,
        BigDecimal discountValue,
        String formattedDiscountValue,
        BigDecimal maxDiscount,
        BigDecimal minOrderValue,
        Integer usageLimit,
        Integer usedCount,
        Integer perUserLimit,
        LocalDateTime startAt,
        LocalDateTime endAt,
        Boolean isActive,
        boolean isExpired,
        boolean isUsable
) {}

