package com.vietspecialty.mapper;

import com.vietspecialty.domain.entity.Voucher;
import com.vietspecialty.domain.enums.DiscountType;
import com.vietspecialty.dto.response.VoucherResponse;
import com.vietspecialty.util.MoneyUtils;

import java.time.LocalDateTime;

public final class VoucherMapper {

    private VoucherMapper() {}

    public static VoucherResponse toResponse(Voucher v) {
        if (v == null) return null;

        boolean isExpired = v.getEndAt() != null && LocalDateTime.now().isAfter(v.getEndAt());
        boolean limitReached = v.getUsageLimit() != null && v.getUsedCount() >= v.getUsageLimit();
        boolean isUsable = Boolean.TRUE.equals(v.getIsActive()) && !isExpired && !limitReached;

        String formattedDiscountValue;
        if (v.getDiscountType() == DiscountType.PERCENT) {
            formattedDiscountValue = v.getDiscountValue().stripTrailingZeros().toPlainString() + "%";
        } else {
            formattedDiscountValue = MoneyUtils.formatVnd(v.getDiscountValue());
        }

        return new VoucherResponse(
                v.getId(),
                v.getCode(),
                v.getName(),
                v.getScope(),
                v.getShop() != null ? v.getShop().getId() : null,
                v.getShop() != null ? v.getShop().getName() : "Toàn sàn VietSpecialty",
                v.getDiscountType(),
                v.getDiscountValue(),
                formattedDiscountValue,
                v.getMaxDiscount(),
                v.getMinOrderValue(),
                v.getUsageLimit(),
                v.getUsedCount(),
                v.getPerUserLimit(),
                v.getStartAt(),
                v.getEndAt(),
                v.getIsActive(),
                isExpired,
                isUsable
        );
    }
}

